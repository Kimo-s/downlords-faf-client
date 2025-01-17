package com.faforever.client.ui.tray;

import com.faforever.client.fx.FxApplicationThreadExecutor;
import com.faforever.client.i18n.I18n;
import com.faforever.client.ui.StageHolder;
import com.faforever.client.ui.tray.event.UpdateApplicationBadgeEvent;
import com.faforever.client.ui.tray.event.UpdateApplicationBadgeEvent.Delta;
import com.faforever.client.ui.tray.event.UpdateApplicationBadgeEvent.NewValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.TextAlignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.IntStream;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;

@Component
@RequiredArgsConstructor
public class TrayIconManager {

  private final I18n i18n;
  private final FxApplicationThreadExecutor fxApplicationThreadExecutor;

  private int badgeCount;

  /**
   * Generates and returns a tray icon. If {@code badgeCount} is greater than 0, a badge (circle) with the badge count
   * generated on top of the icon.
   */
  public void onSetApplicationBadgeEvent(UpdateApplicationBadgeEvent event) {
    switch (event) {
      case Delta(Integer value) -> badgeCount += value;
      case NewValue(Integer value) -> badgeCount = value;
    }

    fxApplicationThreadExecutor.execute(() -> {
      List<Image> icons;
      if (badgeCount < 1) {
        icons = IntStream.range(4, 9)
            .mapToObj(power -> generateTrayIcon((int) Math.pow(2, power)))
            .toList();
      } else {
        icons = IntStream.range(4, 9)
            .mapToObj(power -> generateTrayIcon((int) Math.pow(2, power)))
            .map(image -> addBadge(image, badgeCount))
            .toList();
      }
      StageHolder.getStage().getIcons().setAll(icons);
    });
  }

  private Image addBadge(Image icon, int badgeCount) {
    int badgeIconSize = (int) (icon.getWidth() * 0.6f);

    BufferedImage appIcon = SwingFXUtils.fromFXImage(icon, null);

    Graphics2D appIconGraphics = appIcon.createGraphics();
    appIconGraphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (badgeIconSize * .8)));
    appIconGraphics.setRenderingHints(new RenderingHints(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON));
    appIconGraphics.setColor(new java.awt.Color(244, 67, 54));

    int badgeX = appIcon.getWidth() - badgeIconSize;
    int badgeY = appIcon.getHeight() - badgeIconSize;
    appIconGraphics.fillOval(badgeX, badgeY, badgeIconSize, badgeIconSize);

    String numberText = i18n.number(badgeCount);

    int numberX = appIcon.getWidth() - badgeIconSize / 2 - appIconGraphics.getFontMetrics().stringWidth(numberText) / 2;
    int numberY = appIcon.getHeight() - badgeIconSize / 2 + (appIconGraphics.getFontMetrics().getAscent() - appIconGraphics.getFontMetrics().getDescent()) / 2;

    appIconGraphics.setColor(java.awt.Color.WHITE);
    appIconGraphics.drawString(numberText, numberX, numberY);
    return SwingFXUtils.toFXImage(appIcon, new WritableImage(appIcon.getWidth(), appIcon.getHeight()));
  }

  private Image generateTrayIcon(int dimension) {
    Canvas canvas = new Canvas(dimension, dimension);

    WritableImage writableImage = new WritableImage(dimension, dimension);

    GraphicsContext graphicsContext2D = canvas.getGraphicsContext2D();
    graphicsContext2D.setTextAlign(TextAlignment.CENTER);
    graphicsContext2D.setTextBaseline(VPos.CENTER);
    graphicsContext2D.setFontSmoothingType(FontSmoothingType.LCD);
    graphicsContext2D.setFont(javafx.scene.text.Font.loadFont(TrayIconManager.class.getResourceAsStream("/font/dfc-icons.ttf"), dimension));
    graphicsContext2D.setFill(Color.BLACK);
    graphicsContext2D.fillOval(0, 0, dimension, dimension);
    graphicsContext2D.setFill(Color.WHITE);
    graphicsContext2D.fillText("\uE901", dimension / 2d, dimension / 2d);

    SnapshotParameters snapshotParameters = new SnapshotParameters();
    snapshotParameters.setFill(javafx.scene.paint.Color.TRANSPARENT);
    return fixImage(canvas.snapshot(snapshotParameters, writableImage));
  }

  /**
   * See <a href="http://stackoverflow.com/questions/41029931/snapshot-image-cant-be-used-as-stage-icon">http://stackoverflow.com/questions/41029931/snapshot-image-cant-be-used-as-stage-icon</a>
   */
  private Image fixImage(Image image) {
    return SwingFXUtils.toFXImage(SwingFXUtils.fromFXImage(image, null), null);
  }
}
