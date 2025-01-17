package com.faforever.client.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletionException;

@Slf4j
public final class ConcurrentUtil {
  public static Throwable unwrapIfCompletionException(Throwable throwable) {
    return throwable instanceof CompletionException ? unwrapIfCompletionException(throwable.getCause()) : throwable;
  }
}
