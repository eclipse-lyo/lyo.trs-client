/*
 * Copyright (c) 2016-2017   KTH Royal Institute of Technology.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 *
 * Omar Kacimi         -  Initial implementation
 * Andrew Berezovskyi  -  Lyo contribution updates
 */
package org.eclipse.lyo.oslc4j.trs.consumer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.lyo.oslc4j.trs.consumer.handlers.TrsProviderHandler;
import org.eclipse.lyo.oslc4j.trs.consumer.httpclient.TRSHttpClient;
import org.eclipse.lyo.oslc4j.trs.consumer.util.TrsConfigurationLoader;
import org.eclipse.lyo.oslc4j.trs.consumer.util.TrsConsumerConfiguration;
import org.eclipse.lyo.oslc4j.trs.consumer.util.TrsConsumerUtils;
import org.eclipse.lyo.oslc4j.trs.consumer.util.TrsProviderConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main entry point to the consumer. This class sets up the configuration ,loads the TRS
 * Providers from their respective configuration files and starts the periodic loop including all
 * the involved TRS Providers
 *
 * @author Omar
 */

public class TRSClient {
    private final static Logger logger = LoggerFactory.getLogger(TRSClient.class);
    private final static String MQTT_CLIENT_ID = "TRSClient";
    private final static String PROP_SPARQLQUERYBASE = "sparqlQueryBase";
    private final static String PROP_SPARQLUPDATEBASE = "sparqlUpdateBase";
    private final static String PROP_LOGLEVEL = "LogLevel";
    private final static String PROP_BASEAUTHUSER = "baseAuth_user";
    private final static String PROP_BASEAUTHPWD = "baseAuth_pwd";
    private final static String trsClientFolderName = "TrsClient";
    private final static String trsClientUnixFolderName = ".TrsClient";
    private final static String trsClientlogFileName = "TRSClient.log";
    private final static String trsClientConfigFileName = "trsClient.properties";
    /*
     * Sparql credentials to comunicate with the triplestore and sparql
     * endpoints of the triplestore
     */
    private static String sparqlUpdateBase = "";
    private static String sparqlQueryBase = "";
    private static String sparql_user = "";
    private static String sparql_pwd = "";
    /*
     * Log level read from the configuration file of the TRS Consumer from the
     * user
     */
    private static String logLevel;

    /*
     * http client instance shared between all the TRS providers
     */
    private static TRSHttpClient trsHttpClient = new TRSHttpClient();

    /*
     * Config files locations. These are expected to be in a specific user
     * defined location which is different according to the OS on which the TRS
     * provider is deployed. Please check the documentation for more information
     */
    private static String appDataPath;
    private static String trsProvidersConfigPath;

    private static String trsClientConfigPath;

    /**
     * 1. cconfiguration of the logging level given by the user from the trs client config file 2.
     * loading of the TRS providers from the config folder and creation of the TRS providers objects
     * 3. Starting the TRS providers loop
     */
    public static void main(String[] args) {


        /*==========================================
         * Load config from filesystem
         *=========================================*/

        initialiseFromFilesystem();

        /*==========================================
         * Overall consumer config
         *=========================================*/

        final int pollingPeriod = 20; // seconds
        final int numCores = Runtime.getRuntime().availableProcessors();
        final Random random = new Random(System.currentTimeMillis());

        // this is a networking-bound task, so it makes sense to overshoot the number of threads
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Math.max(16, numCores * 2));

        TrsConsumerConfiguration consumerConfiguration = new TrsConsumerConfiguration(sparqlQueryBase, sparqlUpdateBase,
                sparql_user, sparql_pwd, trsHttpClient, MQTT_CLIENT_ID, scheduler);

        /*==========================================
         * Per provider config
         *=========================================*/

        Collection<TrsProviderConfiguration> connectionConfigurations;
        try {
            connectionConfigurations = loadProviderConfigurations();
        } catch (IOException e) {
            logger.error("Failed to load provider configurations");
            throw new IllegalStateException();
        }

        /*==========================================
         * Initialisation
         *=========================================*/

        final List<TrsProviderHandler> trsProviders = TrsConsumerUtils.loadTrsProviders(consumerConfiguration,
                connectionConfigurations);

        for (TrsProviderHandler trsProvider : trsProviders) {
            // randomly delay each provider to evenly distribute polling load on the thread pool
            scheduler.scheduleAtFixedRate(trsProvider, random.nextInt(pollingPeriod), pollingPeriod, TimeUnit.SECONDS);
        }
    }

    private static void initialiseFromFilesystem() {
        defaultDirectory();

        final String configPath = appDataPath + File.separator + "config";
        final String logFilePath = appDataPath + File.separator + "log" + File.separator + trsClientlogFileName;

        trsClientConfigPath = configPath + File.separator + trsClientConfigFileName;
        trsProvidersConfigPath = configPath + File.separator + "trsProviders";

        boolean trsClientConfig = loadTrsClientProps();
        if (!trsClientConfig) {
            logger.error("Error loading TRS client configuration. Ensure the configuration is valid.");
            throw new IllegalStateException();
        }
        updateLog4jConfiguration(logFilePath);
    }

    private static List<TrsProviderConfiguration> loadProviderConfigurations() throws IOException {
        File trsClientConfigsParentFile = new File(trsProvidersConfigPath);
        final File[] files = trsClientConfigsParentFile.listFiles();
        if (files == null) {
            throw new IllegalStateException(
                    "Directory is not readable, check its existence and permissions: " + trsClientConfigPath);
        }
        final ArrayList<TrsProviderConfiguration> configs = new ArrayList<>(files.length);
        for (File trsProviderConfigFile : files) {
            final TrsProviderConfiguration cfg = TrsConfigurationLoader.from(trsProviderConfigFile);
            configs.add(cfg);
        }
        return configs;
    }

    /**
     * loads the client properties including the sparql endpoints and the credentials to use for the
     * sparql http endpoints
     *
     * @return returns false in case the configuration info is loaded successfuly and false
     * otherwise
     */
    private static boolean loadTrsClientProps() {
        Properties prop = new Properties();
        InputStream input = null;

        try {
            File trsClientConfigFile = new File(trsClientConfigPath);
//            trsClientConfigFile.getAbsolutePath();
            input = new FileInputStream(trsClientConfigFile);

            prop.load(input);

            sparqlUpdateBase = prop.getProperty(PROP_SPARQLUPDATEBASE);
            sparqlQueryBase = prop.getProperty(PROP_SPARQLQUERYBASE);

            logLevel = prop.getProperty(PROP_LOGLEVEL);

            sparql_user = prop.getProperty(PROP_BASEAUTHUSER);
            sparql_pwd = prop.getProperty(PROP_BASEAUTHPWD);

        } catch (IOException ex) {
            logger.error("error loading properties from '{}'", trsClientConfigPath, ex);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    logger.error("error closing the stream", e);
                }
            }
        }
        return sparqlQueryBase != null && !sparqlQueryBase.isEmpty() && !sparqlUpdateBase.isEmpty();

    }

    /**
     * Set the log level to the value provide by the user in the config file and the log file
     * appender to the appropriate location
     *
     * @param logFile path to which the file appender file param is set
     */
    private static void updateLog4jConfiguration(String logFile) {

        Properties props = new Properties();
        try {
            InputStream configStream = TRSClient.class.getResourceAsStream("/log4j.properties");
            props.load(configStream);
            configStream.close();
        } catch (IOException e) {
            logger.error("Errornot laod configuration file ");
        }
        props.put("log4j.appender.FILE.file", logFile);
        LogManager.resetConfiguration();
        PropertyConfigurator.configure(props);
        if (logLevel != null && !logLevel.isEmpty()) {
            LogManager.getRootLogger().setLevel(getLoggingLevel(logLevel));
        }
    }

    /**
     * @param loggingLevelString log level string value given by the user and read from the config
     *                           file
     *
     * @return a log4j logging level according to the log level input from the user
     */
    private static Level getLoggingLevel(String loggingLevelString) {
        switch (loggingLevelString) {
            case "TRACE":
                return Level.TRACE;
            case "DEBUG":
                return Level.DEBUG;

            case "INFO":
                return Level.INFO;

            case "WARN":
                return Level.WARN;

            case "ERROR":
                return Level.ERROR;

            case "FATAL":
                return Level.FATAL;

            case "OFF":
                return Level.OFF;

            case "ALL":
                return Level.ALL;
            default:
                break;
        }
        return null;
    }

    /**
     * According to the os return the directory used to store the application data including the trs
     * client configuration and the logging
     */
    private static void defaultDirectory() {
        String OS = System.getProperty("os.name").toUpperCase();

        if (OS.contains("WIN")) {
            appDataPath = System.getenv("APPDATA");
            appDataPath = appDataPath + File.separator + trsClientFolderName;
        } else if (OS.contains("MAC")) {
            appDataPath = System.getProperty("user.home") + "/Library/Application " + "Support";
            appDataPath = appDataPath + File.separator + trsClientFolderName;
        } else if (OS.contains("NUX")) {
            appDataPath = System.getProperty("user.home");
            appDataPath = appDataPath + File.separator + trsClientUnixFolderName;
        }

    }

}
