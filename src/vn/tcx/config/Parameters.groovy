package vn.tcx.config

@Singleton
class Parameters {
    
    public String PATH_SYS_GLOBAL_FILE = "vn/tcx/sys-config/config"

    

    // path config json dev
    public String PATH_SYS_CONFIG_DEV = "vn/tcx/sys-config/dev-test/config.json"

    // path config json stag
    public String PATH_SYS_CONFIG_STAG_LIVE = "vn/tcx/sys-config/stag-live/config.json"

    /****
    -----------------------------------------------------
    -----------------------------------------------------
    PATH APPLICATION FILE SETTING FOR EACH  JOB 
    -----------------------------------------------------
    -----------------------------------------------------
     ***/

    // path file config file api
    public String PATH_SYS_APPSETTING_FILE_API = "vn/tcx/file-api/app"

    // path file application api tomcat
    public String PATH_SYS_APPLICATION_API = "vn/tcx/application"

    // path file enviroment fe
    public String PATH_SYS_ENV_FE = "vn/tcx/fe/enviroment"

    // path file application index api
    public String PATH_SYS_APPLICATION_INDEX = "vn/tcx/index-api"
    
    /****
    -----------------------------------------------------
    -----------------------------------------------------
    END
    -----------------------------------------------------
    -----------------------------------------------------
     ***/

    // set app env
    public String APP_ENV;

    // set common name 
    public String COMMON_NAME;

    // set job name 
    public String JOB_NAME;

    // common name to get common config api. see in config.json
    public String commonName;

    // get server to deploy. see groups server in config.json
    public String groupServers;

    // get name of api ex: danh-muc, he-thong
    public String jobName;
}
