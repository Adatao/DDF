/**
 * 
 */
package com.adatao.ddf.misc;


import java.io.IOException;
import com.adatao.ddf.util.ConfigHandler;
import com.adatao.ddf.util.Utils;
import com.adatao.ddf.util.ConfigHandler.Configuration;
import com.adatao.ddf.util.IHandleConfig;
import com.google.common.base.Strings;


/**
 * Groups all the configuration-related code that would otherwise crowd up DDF.java
 */
public class Config {

  /**
   * Returns the runtime local-storage directory path name, creating one if necessary.
   * 
   * @return
   * @throws IOException
   */
  public static String getRuntimeDir() throws IOException {
    return Utils.locateOrCreateDirectory(getGlobalValue(ConfigConstant.FIELD_RUNTIME_DIR));
  }

  public static String getLocalPersistenceDir() throws IOException {
    return String.format("%s/%s", getRuntimeDir(),
        getGlobalValue(ConfigConstant.FIELD_LOCAL_PERSISTENCE_DIRECTORY));
  }


  public static String getValue(ConfigConstant section, ConfigConstant key) {
    return getValue(section.toString(), key.toString());
  }

  public static String getValue(String section, ConfigConstant key) {
    return getValue(section, key.toString());
  }

  public static String getValue(String section, String key) {
    return Config.getConfigHandler().getValue(section, key);
  }

  public static String getGlobalValue(ConfigConstant key) {
    return getValue(ConfigConstant.SECTION_GLOBAL.toString(), key.toString());
  }

  public static String getGlobalValue(String key) {
    return getValue(ConfigConstant.SECTION_GLOBAL.toString(), key);
  }

  public static IHandleConfig getConfigHandler() {
    if (sConfigHandler == null) {
      String configFileName = System.getenv(ConfigConstant.DDF_INI_ENV_VAR.toString());
      if (Strings.isNullOrEmpty(configFileName)) configFileName = ConfigConstant.DDF_INI_FILE_NAME.toString();
      sConfigHandler = new ConfigHandler(ConfigConstant.DDF_CONFIG_DIR.toString(), configFileName);

      if (sConfigHandler.getConfig() == null) {
        // HACK: prep a basic default config!
        Configuration config = new Configuration();

        config.getSection(ConfigConstant.SECTION_GLOBAL.toString()) //
            .set("Namespace", "com.example") //
            .set("RuntimeDir", "ddf-runtime") //
            .set("LocalPersistenceDir", "local-ddf-db") //
            .set("DDF", "com.adatao.ddf.DDF") //
            .set("com.adatao.ddf.DDF", "com.adatao.ddf.DDFManager") //
            .set("ISupportStatistics", "com.adatao.ddf.analytics.StatisticsSupporter") //
            .set("IHandleRepresentations", "com.adatao.ddf.content.RepresentationHandler") //
            .set("IHandleSchema", "com.adatao.ddf.content.SchemaHandler") //
            .set("IHandleViews", "com.adatao.ddf.content.ViewHandler") //
            .set("IHandlePersistence", "com.adatao.local.ddf.content.PersistenceHandler") //
            .set("IHandleMetaData", "com.adatao.ddf.content.MetaDataHandler") //
        ;

        config.getSection("local") //
            .set("DDF", "com.adatao.local.ddf.LocalDDF") //
            .set("DDFManager", "com.adatao.local.ddf.LocalDDFManager") //
        ;

        config.getSection("spark") //
            .set("DDF", "com.adatao.spark.ddf.SparkDDF") //
            .set("DDFManager", "com.adatao.spark.ddf.SparkDDFManager") //
            .set("ISupportStatistics", "com.adatao.spark.ddf.analytics.StatisticsSupporter") //
            .set("IHandleMetaData", "com.adatao.spark.ddf.content.MetaDataHandler") //
            .set("IHandleRepresentations", "com.adatao.spark.ddf.content.RepresentationHandler") //
            .set("IHandleSchema", "com.adatao.spark.ddf.content.SchemaHandler") //
            .set("IHandleSql", "com.adatao.spark.ddf.etl.SqlHandler") //
            .set("IHandleViews", "com.adatao.spark.ddf.content.ViewHandler") //
            .set("ISupportML", "com.adatao.spark.ddf.analytics.MLSupporter") //
        ;

        sConfigHandler.setConfig(config);
      }
    }

    return sConfigHandler;
  }


  static IHandleConfig sConfigHandler;



  // ////// Global/Static Fields & Methods ////////

  // //// Global configuration handling //////

  public enum ConfigConstant {
    // @formatter:off
    
    DDF_INI_ENV_VAR("DDF_INI"), DDF_INI_FILE_NAME("ddf.ini"), DDF_CONFIG_DIR("ddf-conf"),
    
    ENGINE_NAME_DEFAULT("spark"), ENGINE_NAME_LOCAL("local"), ENGINE_NAME_SPARK("spark"), 
    
    SECTION_GLOBAL("global"), 
    
    FIELD_RUNTIME_DIR("RuntimeDir"), FIELD_NAMESPACE("Namespace"), FIELD_DDF("DDF"), FIELD_DDF_MANAGER("DDFManager"),
    FIELD_LOCAL_PERSISTENCE_DIRECTORY("LocalPersistenceDir")
    
    ;
    // @formatter:on

    private String mValue;


    private ConfigConstant(String value) {
      mValue = value;
    }

    @Override
    public String toString() {
      return mValue;
    }
  }
}
