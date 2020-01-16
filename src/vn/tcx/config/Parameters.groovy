package vn.tcx.config

@Singleton
class Parameters {

    // path config json dev
    public String PATH_SYS_CONFIG_DEV = "vn/tcx/sys-config/dev-test/config.json"

    // path config json stag
    public String PATH_SYS_CONFIG_STAG_LIVE = "vn/tcx/sys-config/stag-live/config.json"

    // common name to get common config api. see in config.json
    public String commonName;

    // get server to deploy. see groups server in config.json
    public String groupServers;

    // get name of api ex: danh-muc, he-thong
    public String jobName;
}
