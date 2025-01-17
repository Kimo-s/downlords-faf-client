package com.faforever.client.fa;

import com.faforever.client.builders.PlayerBeanBuilder;
import com.faforever.client.logging.LoggingService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.DataPrefs;
import com.faforever.client.preferences.ForgedAlliancePrefs;
import com.faforever.client.test.ServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ForgedAllianceServiceTest extends ServiceTest {
  @InjectMocks
  @Spy
  private ForgedAllianceService instance;
  @Mock
  private PlayerService playerService ;
  @Mock
  private LoggingService loggingService;

  @Spy
  private ForgedAlliancePrefs forgedAlliancePrefs;
  @Spy
  private DataPrefs dataPrefs;

  @BeforeEach
  public void setUp() throws Exception {
    dataPrefs.setBaseDataDirectory(Path.of("."));
    when(playerService.getCurrentPlayer()).thenReturn(PlayerBeanBuilder.create().defaultValues().get());
  }

  @Test
  public void testStartGameOffline() throws Exception {
    IOException throwable = assertThrows(IOException.class, () -> instance.startGameOffline("test"));
    assertThat(throwable.getCause().getMessage(), containsString("error=2"));

    verify(loggingService).getNewGameLogFile(0);
  }

  @Test
  public void testStartGameOnline() throws Exception {
    GameParameters gameParameters = new GameParameters();
    gameParameters.setUid(1);
    gameParameters.setLocalGpgPort(0);
    gameParameters.setLocalReplayPort(0);
    gameParameters.setLeaderboard("test");
    IOException throwable = assertThrows(IOException.class, () -> instance.startGameOnline(gameParameters));
    assertThat(throwable.getCause().getMessage(), containsString("error=2"));

    verify(playerService).getCurrentPlayer();
    verify(loggingService).getNewGameLogFile(gameParameters.getUid());
  }

  @Test
  public void testStartReplay() throws Exception {
    IOException throwable = assertThrows(IOException.class, () -> instance.startReplay(Path.of("."), 0));
    assertThat(throwable.getCause().getMessage(), containsString("error=2"));

    verify(loggingService).getNewGameLogFile(0);
    verify(instance).getReplayExecutablePath();
  }

  @Test
  public void testStartOnlineReplay() throws Exception {
    IOException throwable = assertThrows(IOException.class, () -> instance.startReplay(URI.create("google.com"), 0));
    assertThat(throwable.getCause().getMessage(), containsString("error=2"));

    verify(playerService).getCurrentPlayer();
    verify(loggingService).getNewGameLogFile(0);
    verify(instance).getReplayExecutablePath();
  }
}
