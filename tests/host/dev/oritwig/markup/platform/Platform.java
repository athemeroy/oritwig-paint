package dev.oritwig.markup.platform;
/** Test-only deterministic dispatcher. Runtime uses Android Handler. */
public final class Platform {public static void runOnUIThread(Runnable r){r.run();}public static void log(Object o){throw new AssertionError(o.toString());}}
