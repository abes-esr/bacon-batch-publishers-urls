package fr.abes.bacon.baconediteurs.batch.service;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FtpService {
    @Value("${ebsco.filepath}")
    private String path;
    private final FTPClient client;

    public FtpService() {
        this.client = new FTPClient();
    }

    public void connect(String host, String username, String password, String path) throws IOException {
        client.connect(host);
        client.login(username, password);
        client.changeWorkingDirectory(path);
        client.enterLocalPassiveMode();
    }

    public String getLastModificationDate(String filename) throws IOException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        FTPFile file = client.listFiles(filename)[0];
        if (file != null) {
            return format.format(file.getTimestamp().getTime());
        }
        throw new IllegalArgumentException("Fichier introuvable !");
    }

    public boolean getFile(String filename, String localPath) throws IOException {
        OutputStream stream = new FileOutputStream(localPath + filename);
        boolean success = client.retrieveFile(filename, stream);
        stream.close();
        return success;
    }

    public void disconnect() throws IOException {
        if (client.isConnected()) {
            client.logout();
            client.disconnect();
        }
    }

    public List<String> listFilesByPattern(Pattern pattern) throws IOException {
        // Récupération des noms de fichiers
        String[] nomsFichiers = client.listNames();
        if (nomsFichiers == null) return List.of(); // cas de répertoire vide ou erreur

        return Arrays.stream(nomsFichiers)
                .filter(name -> pattern.matcher(name).matches())
                .map(name -> {
                    int idx = name.lastIndexOf("_");
                    if (idx == -1 || !name.endsWith(".txt")) return null;
                    String base = name.substring(0, idx);
                    String date = name.substring(idx + 1, name.length() - 4);
                    return new String[] { base, date, name }; // structure légère
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        parts -> parts[0], // base
                        Collectors.maxBy(Comparator.comparing(parts -> parts[1])) // max date
                ))
                .values().stream()
                .filter(Optional::isPresent)
                .map(opt -> opt.get()[2]) // récupérer le nom complet
                .collect(Collectors.toList());
    }
}
