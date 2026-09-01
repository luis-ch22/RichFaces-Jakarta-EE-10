package org.richfaces.component.fileUpload;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;

import org.richfaces.event.FileUploadEvent;
import org.richfaces.model.UploadedFile;

@Named
@RequestScoped
public class FileUploadBean {

    private UploadedFile uploadedFile;

    public void listener(FileUploadEvent event) throws Exception {
        uploadedFile = event.getUploadedFile();
    }

    public UploadedFile getUploadedFile() {
        return uploadedFile;
    }
}
