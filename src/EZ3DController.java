import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.controlsfx.control.textfield.TextFields;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;

public class EZ3DController extends Main {
    @FXML
    public Label emailLabel;
    @FXML
    public TextField emailTextField;
    @FXML
    public GridPane filesGrid;

    private ClipboardContent filesToCopyClipboard = new ClipboardContent();

    @FXML
    public void initialize() {
        ArrayList<String> users = new ArrayList<>();
        for (File dir:findDir(""))
            users.add(dir.getName());
        TextFields.bindAutoCompletion(emailTextField, users);
    }

    @FXML
    public void closeProgram() {
        System.exit(1);
    }

    @FXML
    public void refresh() {
        try {
            refreshFiles();
        }catch(Exception e) {
            System.exit(1);
        }
    }

    @FXML
    public void openDocumentation() throws Exception{
        if(Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("https://github.com/GilliamJonathan/EZ3D/wiki"));
        }
    }

    // DISABLED
    @FXML
    public void openConfigFile() {

    }

    // DISABLED
    @FXML
    public void openAbout() {

    }

    @FXML
    private void searchUser() {
        String query = emailTextField.getText();
        if (findDir(query).size() == 1){
            File user = findDir(query).get(0);
            File[] files = user.listFiles();
                showFiles(files);
            emailLabel.setText(user.getName());
        }
    }

    private void showFiles(File[] files) {
        filesGrid.getChildren().clear();
        for (int i = 0; i < files.length; i++) {
            ImageView image = new ImageView("simplify3dIcon.png");
            image.setFitHeight(80);
            image.setFitWidth(80);

            Label text = new Label(((new Date().getTime() - files[i].lastModified()) / (1000 * 60 * 60)) + " hours old");
            text.setMaxWidth(80);
            text.setFont(Font.font("Verdana", FontWeight.BOLD, 10));
            text.setAlignment(Pos.CENTER);

            VBox vb = new VBox();
            vb.getChildren().add(image);
            vb.getChildren().add(text);
            vb.setAlignment(Pos.CENTER);
            vb.setMinSize(95, 100);
            vb.setUserData(files[i]);
            // Makes drag an drop work like file explore
            vb.setOnDragDetected(me -> {
                Dragboard db = vb.startDragAndDrop(TransferMode.ANY);

                File file = ((File)vb.getUserData());

                ArrayList<File> dragFiles = new ArrayList<>();
                dragFiles.add(file);
                filesToCopyClipboard.putFiles(dragFiles);

                db.setContent(filesToCopyClipboard);
                me.consume();
                System.out.println("1");
            });
            vb.setOnDragDone(me -> {
                filesToCopyClipboard.clear();
            });

            filesGrid.add(vb, i%5, i/5);

            if (i == 4)
                break;
        }
    }
}
