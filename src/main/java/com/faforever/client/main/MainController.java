package com.faforever.client.main;

import ch.micheljung.fxwindow.FxStage;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.exception.AssetLoadException;
import com.faforever.client.fx.FxApplicationThreadExecutor;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.NodeController;
import com.faforever.client.headerbar.HeaderBarController;
import com.faforever.client.i18n.I18n;
import com.faforever.client.login.LoginController;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.NavigationItem;
import com.faforever.client.navigation.NavigationHandler;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.ImmediateNotificationController;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.notification.ServerNotificationController;
import com.faforever.client.notification.Severity;
import com.faforever.client.notification.TransientNotificationsController;
import com.faforever.client.preferences.NotificationPrefs;
import com.faforever.client.preferences.WindowPrefs;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.StageHolder;
import com.faforever.client.ui.alert.Alert;
import com.faforever.client.ui.alert.animation.AlertAnimation;
import com.faforever.client.ui.tray.TrayIconManager;
import com.faforever.client.ui.tray.event.UpdateApplicationBadgeEvent;
import com.faforever.client.user.LoginService;
import com.faforever.client.util.PopupUtil;
import com.google.common.annotations.VisibleForTesting;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.PopupWindow.AnchorLocation;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static javafx.scene.layout.Background.EMPTY;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
@RequiredArgsConstructor
public class MainController extends NodeController<Node> implements InitializingBean {

  private final ClientProperties clientProperties;
  private final I18n i18n;
  private final NotificationService notificationService;
  private final UiService uiService;
  private final LoginService loginService;
  private final NotificationPrefs notificationPrefs;
  private final WindowPrefs windowPrefs;
  private final FxApplicationThreadExecutor fxApplicationThreadExecutor;
  private final TrayIconManager trayIconManager;
  private final NavigationHandler navigationHandler;

  public Pane contentPane;
  public StackPane contentWrapperPane;
  public StackPane mainRoot;
  public HBox headerBar;
  public HeaderBarController headerBarController;

  @VisibleForTesting
  protected Popup transientNotificationsPopup;
  private FxStage fxStage;

  @Override
  public void afterPropertiesSet() {
    loginService.loggedInProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue) {
        enterLoggedInState();
      } else {
        enterLoggedOutState();
      }
    });
  }

  /**
   * Hides the install4j splash screen. The hide method is invoked via reflection to accommodate starting the client
   * without install4j (e.g. on linux).
   */
  private static void hideSplashScreen() {
    try {
      final Class<?> splashScreenClass = Class.forName("com.install4j.api.launcher.SplashScreen");
      final Method hideMethod = splashScreenClass.getDeclaredMethod("hide");
      hideMethod.invoke(null);
    } catch (ClassNotFoundException e) {
      log.info("No install4j splash screen found to close.");
    } catch (NoSuchMethodException | IllegalAccessException e) {
      log.error("Couldn't close install4j splash screen.", e);
    } catch (InvocationTargetException e) {
      log.error("Couldn't close install4j splash screen.", e.getCause());
    }
  }

  @Override
  protected void onInitialize() {
    TransientNotificationsController transientNotificationsController = uiService.loadFxml("theme/transient_notifications.fxml");
    transientNotificationsPopup = PopupUtil.createPopup(transientNotificationsController.getRoot());
    transientNotificationsPopup.getScene().getRoot().getStyleClass().add("transient-notification");

    transientNotificationsController.getRoot()
        .getChildren()
        .addListener(new ToastDisplayer(transientNotificationsController));

    notificationService.addImmediateNotificationListener(notification -> fxApplicationThreadExecutor.execute(() -> displayImmediateNotification(notification)));
    notificationService.addServerNotificationListener(notification -> fxApplicationThreadExecutor.execute(() -> displayServerNotification(notification)));
    notificationService.addTransientNotificationListener(notification -> fxApplicationThreadExecutor.execute(() -> transientNotificationsController.addNotification(notification)));

    navigationHandler.navigationEventProperty().subscribe(this::onNavigateEvent);
  }

  private void displayView(NodeController<?> controller, NavigateEvent navigateEvent) {
    controller.display(navigateEvent);
    Node node = controller.getRoot();
    contentPane.getChildren().setAll(node);
    JavaFxUtil.setAnchors(node, 0d);
  }

  private Rectangle2D getTransientNotificationAreaBounds() {
    ObservableList<Screen> screens = Screen.getScreens();

    int toastScreenIndex = notificationPrefs.getToastScreen();
    Screen screen;
    if (toastScreenIndex < screens.size()) {
      screen = screens.get(Math.max(0, toastScreenIndex));
    } else {
      screen = Screen.getPrimary();
    }
    return screen.getVisualBounds();
  }

  public void display() {
    trayIconManager.onSetApplicationBadgeEvent(UpdateApplicationBadgeEvent.ofNewValue(0));

    Stage stage = StageHolder.getStage();

    int width = windowPrefs.getWidth();
    int height = windowPrefs.getHeight();

    stage.setMinWidth(10);
    stage.setMinHeight(10);
    stage.setWidth(width);
    stage.setHeight(height);
    stage.show();

    hideSplashScreen();
    enterLoggedOutState();

    JavaFxUtil.assertApplicationThread();
    stage.setMaximized(windowPrefs.getMaximized());
    if (!stage.isMaximized()) {
      setWindowPosition(stage, windowPrefs);
    }
    registerWindowListeners();
  }

  private void setWindowPosition(Stage stage, WindowPrefs mainWindowPrefs) {
    double x = mainWindowPrefs.getX();
    double y = mainWindowPrefs.getY();
    int width = mainWindowPrefs.getWidth();
    int height = mainWindowPrefs.getHeight();
    ObservableList<Screen> screensForRectangle = Screen.getScreensForRectangle(x, y, width, height);
    if (screensForRectangle.isEmpty()) {
      JavaFxUtil.centerOnScreen(stage);
    } else {
      stage.setX(x);
      stage.setY(y);
    }
  }

  private void enterLoggedOutState() {
    LoginController loginController = uiService.loadFxml("theme/login/login.fxml");

    fxApplicationThreadExecutor.execute(() -> {
      contentPane.getChildren().clear();
      fxStage.getStage().setTitle(i18n.get("login.title"));

      fxStage.setContent(loginController.getRoot());

      fxStage.getNonCaptionNodes().clear();
    });
  }

  private void registerWindowListeners() {
    Stage stage = fxStage.getStage();
    JavaFxUtil.addListener(stage.heightProperty(), (observable, oldValue, newValue) -> {
      if (!stage.isMaximized()) {
        windowPrefs.setHeight(newValue.intValue());
      }
    });
    JavaFxUtil.addListener(stage.widthProperty(), (observable, oldValue, newValue) -> {
      if (!stage.isMaximized()) {
        windowPrefs.setWidth(newValue.intValue());
      }
    });
    JavaFxUtil.addListener(stage.xProperty(), observable -> {
      if (!stage.isMaximized()) {
        windowPrefs.setX(stage.getX());
      }
    });
    JavaFxUtil.addListener(stage.yProperty(), observable -> {
      if (!stage.isMaximized()) {
        windowPrefs.setY(stage.getY());
      }
    });
    JavaFxUtil.addListener(stage.maximizedProperty(), observable -> {
      windowPrefs.setMaximized(stage.isMaximized());
      if (!stage.isMaximized()) {
        setWindowPosition(stage, windowPrefs);
      }
    });
    windowPrefs.backgroundImagePathProperty().when(showing).subscribe(this::setBackgroundImage);
  }

  private void setBackgroundImage(Path filepath) {
    Image image;
    if (filepath != null && Files.exists(filepath)) {
      try {
        image = new Image(filepath.toUri().toURL().toExternalForm());
        mainRoot.setBackground(new Background(new BackgroundImage(
            image,
            BackgroundRepeat.NO_REPEAT,
            BackgroundRepeat.NO_REPEAT,
            BackgroundPosition.CENTER,
            new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, false, true)
        )));
        return;
      } catch (MalformedURLException e) {
        throw new AssetLoadException("Could not load background image", e, "background.couldNotLoad", filepath);
      }
    }

    mainRoot.setBackground(EMPTY);
  }

  private void enterLoggedInState() {
    fxApplicationThreadExecutor.execute(() -> {
      Stage stage = StageHolder.getStage();
      stage.setTitle(clientProperties.getMainWindowTitle());

      fxStage.setContent(getRoot());
      fxStage.getNonCaptionNodes().setAll(headerBarController.getNonCaptionNodes());
      fxStage.setTitleBar(headerBar);

      openStartTab();
    });
  }

  @VisibleForTesting
  void openStartTab() {
    NavigationItem navigationItem = windowPrefs.getNavigationItem();
    if (navigationItem == null) {
      navigationItem = NavigationItem.NEWS;

      if (!windowPrefs.navigationItemProperty().isBound()) {
        askUserForPreferenceOverStartTab();
      }
    }
    navigationHandler.navigateTo(new NavigateEvent(navigationItem));
  }

  private void askUserForPreferenceOverStartTab() {
    windowPrefs.setNavigationItem(NavigationItem.NEWS);
    List<Action> actions = Collections.singletonList(new Action(i18n.get("startTab.configure"), event ->
        makePopUpAskingForPreferenceInStartTab()));
    notificationService.addNotification(new PersistentNotification(i18n.get("startTab.wantToConfigure"), Severity.INFO, actions));
  }

  private void makePopUpAskingForPreferenceInStartTab() {
    StartTabChooseController startTabChooseController = uiService.loadFxml("theme/start_tab_choose.fxml");
    Action saveAction = new Action(i18n.get("startTab.save"), event -> {
      NavigationItem newSelection = startTabChooseController.getSelected();
      windowPrefs.setNavigationItem(newSelection);
      navigationHandler.navigateTo(new NavigateEvent(newSelection));
    });
    ImmediateNotification notification =
        new ImmediateNotification(i18n.get("startTab.title"), i18n.get("startTab.message"),
            Severity.INFO, null, Collections.singletonList(saveAction), startTabChooseController.getRoot());
    notificationService.addNotification(notification);
  }

  @Override
  public Pane getRoot() {
    return mainRoot;
  }

  public void onNavigateEvent(NavigateEvent navigateEvent) {
    if (navigateEvent == null) {
      return;
    }

    NavigationItem item = navigateEvent.getItem();

    NodeController<?> controller = uiService.loadFxml(item.getFxmlFile());
    displayView(controller, navigateEvent);
  }

  private void displayImmediateNotification(ImmediateNotification notification) {
    Alert<?> dialog = new Alert<>(fxStage.getStage(), fxApplicationThreadExecutor);

    ImmediateNotificationController controller = ((ImmediateNotificationController) uiService.loadFxml("theme/immediate_notification.fxml"))
        .setNotification(notification)
        .setCloseListener(dialog::close);

    dialog.setContent(controller.getDialogLayout());
    dialog.setAnimation(AlertAnimation.TOP_ANIMATION);
    dialog.show();
  }

  private void displayServerNotification(ImmediateNotification notification) {
    Alert<?> dialog = new Alert<>(fxStage.getStage(), fxApplicationThreadExecutor);

    ServerNotificationController controller = ((ServerNotificationController) uiService.loadFxml("theme/server_notification.fxml"))
        .setNotification(notification)
        .setCloseListener(dialog::close);

    dialog.setContent(controller.getDialogLayout());
    dialog.setAnimation(AlertAnimation.TOP_ANIMATION);
    dialog.show();
  }

  public void setFxStage(FxStage fxWindow) {
    this.fxStage = fxWindow;
  }

  public class ToastDisplayer implements InvalidationListener {
    private final TransientNotificationsController transientNotificationsController;

    public ToastDisplayer(TransientNotificationsController transientNotificationsController) {
      this.transientNotificationsController = transientNotificationsController;
    }

    @Override
    public void invalidated(Observable observable) {
      boolean enabled = notificationPrefs.isTransientNotificationsEnabled();
      if (transientNotificationsController.getRoot().getChildren().isEmpty() || !enabled) {
        transientNotificationsPopup.hide();
        return;
      }

      Rectangle2D visualBounds = getTransientNotificationAreaBounds();
      double anchorX = visualBounds.getMaxX() - 1;
      double anchorY = visualBounds.getMaxY() - 1;
      AnchorLocation location = switch (notificationPrefs.getToastPosition()) {
        case BOTTOM_RIGHT -> AnchorLocation.CONTENT_BOTTOM_RIGHT;
        case TOP_RIGHT -> {
          anchorY = visualBounds.getMinY();
          yield AnchorLocation.CONTENT_TOP_RIGHT;
        }
        case BOTTOM_LEFT -> {
          anchorX = visualBounds.getMinX();
          yield AnchorLocation.CONTENT_BOTTOM_LEFT;
        }
        case TOP_LEFT -> {
          anchorX = visualBounds.getMinX();
          anchorY = visualBounds.getMinY();
          yield AnchorLocation.CONTENT_TOP_LEFT;
        }
      };
      transientNotificationsPopup.setAnchorLocation(location);
      transientNotificationsPopup.show(mainRoot.getScene().getWindow(), anchorX, anchorY);
    }
  }
}
