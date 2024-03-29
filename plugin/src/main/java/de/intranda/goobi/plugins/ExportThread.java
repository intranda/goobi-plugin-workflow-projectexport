package de.intranda.goobi.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.goobi.beans.Process;
import org.goobi.beans.Step;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ExportThread extends Thread {

    @Setter
    private String exportFolder;
    @Setter
    private String projectName;
    @Setter
    private List<Process> processesInProject;
    @Setter
    private String imageFolder;
    @Setter
    private String finishStepName;
    @Setter
    private Thread waitforThread;

    @Override
    public void run() {
        if (waitforThread != null) {
            try {
                waitforThread.join();
            } catch (InterruptedException e) {
                log.error(e);
                Helper.setFehlerMeldung("Error exporting project. See application log for details");
                return;
            }
        }
        // copy data
        log.info("Copy content of project {} to export destination. ", projectName);
        processloop: for (Process process : processesInProject) {
            for (Step step : process.getSchritte()) {
                if (finishStepName.equals(step.getTitel()) && step.getBearbeitungsstatusEnum() == StepStatus.DEACTIVATED) {
                    continue processloop;
                }
            }
            log.debug("Export files for process {}", process.getTitel());
            try {
                List<String> filenames = StorageProvider.getInstance().list(process.getImagesTifDirectory(false));
                log.debug("Copy {} files.", filenames.size());
                if (!filenames.isEmpty()) {

                    Path source = Paths.get(process.getConfiguredImageFolder(imageFolder));
                    Path target = Paths.get(exportFolder, projectName, process.getTitel());
                    if (!Files.exists(target)) {
                        Files.createDirectories(target);
                    }
                    StorageProvider.getInstance().copyDirectory(source, target);

                }
            } catch (IOException | SwapException | DAOException e) {
                log.error(e);
            }
        }

        // create zip file
        log.info("Create zip file for project {}. ", projectName);
        Path zipFileName = Paths.get(exportFolder, projectName + ".zip");

        if (StorageProvider.getInstance().isFileExists(zipFileName)) {
            try {
                StorageProvider.getInstance().deleteFile(zipFileName);
            } catch (IOException e) {
                log.error(e);
            }
        }
        try (OutputStream fos = Files.newOutputStream(zipFileName);
                ZipOutputStream out = new ZipOutputStream(fos)) {
            Path project = Paths.get(exportFolder, projectName);
            zipFolder("", project, out);
            out.flush();

        } catch (IOException e) {
            log.error(e);
        }

    }

    /**
     * zip a given folder and go into subfolders recursively
     * 
     * @param zipBasePath the basepath inside of the zip file
     * @param path the folder to be run through
     * @param out the zip output stream
     * @throws IOException
     */
    public static void zipFolder(String zipBasePath, Path path, ZipOutputStream out) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String p = zipBasePath + entry.getFileName() + "/";
                    zipFolder(p, entry, out);
                } else {
                    try (InputStream in = StorageProvider.getInstance().newInputStream(entry)) {
                        out.putNextEntry(new ZipEntry(zipBasePath + entry.getFileName().toString()));
                        byte[] b = new byte[1024];
                        int count;
                        while ((count = in.read(b)) > 0) {
                            out.write(b, 0, count);
                        }
                    }
                }
            }
        }
    }
}
