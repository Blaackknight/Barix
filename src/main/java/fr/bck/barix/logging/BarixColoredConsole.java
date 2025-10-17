package fr.bck.barix.logging;

public final class BarixColoredConsole {
    private static final String PATTERN = "%style{[}{bright_black}%style{%d{HH:mm:ss}}{white}%style{]}{bright_black} " + "%style{[}{bright_black}%style{%t}{cyan}%style{/}{red}" + "%highlight{%p}{FATAL=bg_red bright_white, ERROR=red, WARN=yellow, INFO=green, DEBUG=blue, TRACE=magenta}" + "%style{]}{bright_black} "
            // Affiche uniquement Barix + catégorie issue du MDC (si présente), la catégorie est déjà colorisée
            + "%style{[}{bright_black}%style{Barix}{bright_blue}%notEmpty{%style{/}{red}%mdc{barixCat}}%style{]}{bright_black}: " + "%msg%n%throwable";

    public static void install() {
        var ctx = (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        var cfg = ctx.getConfiguration();
        var layout = org.apache.logging.log4j.core.layout.PatternLayout.newBuilder().withPattern(PATTERN).withDisableAnsi(false).withNoConsoleNoAnsi(false).withConfiguration(cfg).build();
        var app = org.apache.logging.log4j.core.appender.ConsoleAppender.newBuilder().setName("BarixConsole").setTarget(org.apache.logging.log4j.core.appender.ConsoleAppender.Target.SYSTEM_OUT).setLayout(layout).build();
        app.start();
        cfg.addAppender(app);

        cfg.removeLogger("Barix");
        var barixLoggerCfg = new org.apache.logging.log4j.core.config.LoggerConfig("Barix", org.apache.logging.log4j.Level.TRACE, false // additive false -> pas de propagation au root, les enfants Barix.* remontent ici
        );
        barixLoggerCfg.addAppender(app, org.apache.logging.log4j.Level.ALL, null);
        cfg.addLogger("Barix", barixLoggerCfg);


        ctx.updateLoggers();
    }
}