package fr.abes.bacon.baconediteurs.configuration;

import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Plugin(name = "BaconEditeursLogAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class BaconEditeursLogAppender extends AbstractAppender {

    private String pathToLogs = "./local/editeur/logs/";

    private final ConcurrentMap<String, Writer> writers = new ConcurrentHashMap<>();

    protected BaconEditeursLogAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout, true, Property.EMPTY_ARRAY);
    }

    @PluginFactory
    public static BaconEditeursLogAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout) {

        return new BaconEditeursLogAppender(name, filter, layout != null ? layout : PatternLayout.createDefaultLayout());
    }

    @Override
    public void append(LogEvent event) {
        String filename = event.getContextData().getValue("logFileName");
        String editeur = event.getContextData().getValue("editeur");
        String pathToLogFile = pathToLogs.replace("editeur", editeur) + filename;
        if (pathToLogFile.isBlank()) return;
        try {
            Writer writer = writers.computeIfAbsent(pathToLogFile, fn -> {
                try {
                    return new BufferedWriter(new FileWriter(fn, true));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            String message = new String(getLayout().toByteArray(event), StandardCharsets.UTF_8);
            writer.write(message);
            writer.flush();

        } catch (IOException e) {
            LOGGER.error("Erreur lors de l’écriture dans le fichier log du job", e);
        }
    }
}
