package fr.abes.bacon.baconediteurs.batch.service;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;

@Service
public class FtpService {
    @Value("${ebsco.filepath}")
    private String path;
    private FTPClient client;

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
}
