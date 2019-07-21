package net.dirtcraft.dirtlauncher.elements;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.css.PseudoClass;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.TextAlignment;
import net.dirtcraft.dirtlauncher.Controllers.Home;
import net.dirtcraft.dirtlauncher.Main;
import net.dirtcraft.dirtlauncher.backend.components.DiscordPresence;
import net.dirtcraft.dirtlauncher.backend.config.CssClasses;
import net.dirtcraft.dirtlauncher.backend.config.Internal;
import net.dirtcraft.dirtlauncher.backend.objects.Pack;
import net.dirtcraft.dirtlauncher.backend.utils.FileUtils;
import net.dirtcraft.dirtlauncher.backend.utils.MiscUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class PackCell extends Button {

    private double lastDragY;
    final private Pack pack;
    final private ContextMenu contextMenu;

    public PackCell(Pack pack){
        this.pack = pack;
        getStyleClass().add(CssClasses.PACK_CELL);
        contextMenu = new ContextMenu();
        initContextMenu();
        setCursor(Cursor.HAND);
        setFocusTraversable(false);
        setText(pack.getName());
        setMinSize(278, 50);
        setPrefSize(278, 50);
        setMaxSize(278, 50);

        final Tooltip tooltip = new Tooltip();
        tooltip.setTextAlignment(TextAlignment.LEFT);
        tooltip.getStyleClass().add(CssClasses.PACKLIST);

        tooltip.setText(String.join("\n", Arrays.asList(
                "ModPack Name: " + pack.getName(),
                "ModPack Version: " + pack.getVersion(),
                "Minecraft Version: " + pack.getGameVersion(),
                "Forge Version: " + pack.getForgeVersion(),
                "Minimum Ram: " + pack.getRequiredRam() + " GB",
                "Recommended Ram: " + pack.getRecommendedRam() + " GB",
                "Direct Connect IP: " + (!pack.isPixelmon() ? (pack.getCode() + ".DIRTCRAFT").toUpperCase() : "PIXELMON") + ".GG")
        ));

        final Image image = new Image(MiscUtils.getResourceStream(
                Internal.PACK_IMAGES, pack.getFormattedName().toLowerCase() + ".png"),
                128, 128, false, true);

        final ImageView imageView = new ImageView(image);
        imageView.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 10, 0, 0, 0);");

        tooltip.setGraphic(imageView);
        tooltip.setGraphicTextGap(50);

        setTooltip(tooltip);
        setOnMouseDragEntered(e-> lastDragY =  e.getY());
        setOnMouseDragged(this::onDrag);
        setOnMouseDragExited(e-> lastDragY = 0);
        setOnContextMenuRequested(e->{
            if (pack.isInstalled()) {
                contextMenu.show(this, e.getScreenX(), e.getScreenY());
            }
        });

    }

    private void deactivate(){
        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), false);
    }

    public Pack getPack(){
        return pack;
    }
    private void onDrag(MouseEvent event) {
        if (event.isPrimaryButtonDown()) {
            ScrollPane window = (ScrollPane) this.getParent().getParent().getParent().getParent();
            double change = (lastDragY - event.getY()) / window.getHeight();
            window.setVvalue(window.getVvalue() + change);
            lastDragY = change;
        }
    }

    public void fire() {
        final LoginBar home = Home.getInstance().getLoginBar();
        final Button playButton = home.getActionButton();
        home.getActivePackCell().ifPresent(PackCell::deactivate);
        home.setActivePackCell(this);
        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), true);
        DiscordPresence.setDetails("Playing " + pack.getName());

        if (!MiscUtils.isEmptyOrNull(home.getUsernameField().getText().trim(), home.getPassField().getText().trim())) playButton.setDisable(false);
    }

    private void initContextMenu(){
        MenuItem reinstall = new MenuItem("Reinstall");
        MenuItem uninstall = new MenuItem("Uninstall");
        MenuItem openFolder = new MenuItem("Open Folder");
        reinstall.getStyleClass().add(CssClasses.PACK_MENU);
        uninstall.getStyleClass().add(CssClasses.PACK_MENU);
        openFolder.getStyleClass().add(CssClasses.PACK_MENU);
        reinstall.getStyleClass().add(CssClasses.PACK_MENU_OPTION);
        uninstall.getStyleClass().add(CssClasses.PACK_MENU_OPTION);
        openFolder.getStyleClass().add(CssClasses.PACK_MENU_OPTION);
        contextMenu.getStyleClass().add(CssClasses.PACK_MENU);
        contextMenu.setId(CssClasses.PACK_MENU);
        contextMenu.getItems().add(reinstall);
        contextMenu.getItems().add(uninstall);
        contextMenu.getItems().add(openFolder);

        reinstall.setOnAction(e->{
            uninstall.fire();
            LoginBar loginBar = Home.getInstance().getLoginBar();
            Optional<PackCell> oldPack = loginBar.getActivePackCell();
            loginBar.setActivePackCell(this);
            loginBar.getActionButton().installPack(pack);
            oldPack.ifPresent(PackCell::fire);
        });

        uninstall.setOnAction(e->{
            JsonObject instanceManifest = FileUtils.readJsonFromFile(Main.getSettings().getDirectoryManifest(Main.getSettings().getInstancesDirectory()));
            if (instanceManifest == null || !instanceManifest.has("packs")) return;
            JsonArray packs = instanceManifest.getAsJsonArray("packs");
            for (int i = 0; i < packs.size(); i++){
                if (Objects.equals(packs.get(i).getAsJsonObject().get("name").getAsString(), pack.getName())) packs.remove(i);
            }
            FileUtils.writeJsonToFile(new File(Main.getSettings().getDirectoryManifest(Main.getSettings().getInstancesDirectory()).getPath()), instanceManifest);
            try {
                FileUtils.deleteDirectory(pack.getInstanceDirectory());
            } catch (IOException exception){
                Main.getLogger().error(exception);
            }
        });

        openFolder.setOnAction(e->{
            try {
                Desktop.getDesktop().open(pack.getInstanceDirectory());
            } catch (IOException exception){
                Main.getLogger().error(exception);
            }
        });
    }
}
