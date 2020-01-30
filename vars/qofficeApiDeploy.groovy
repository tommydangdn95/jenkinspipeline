// see jenkins manager config in global pipeline libraries to load libraty
import vn.tcx.config.*
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import hudson.model.User;
import hudson.tasks.Mailer;
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils


def call(){
    /* Global variable */
    CURRENTSTATE = ''
    CHANGELOGMESS = ''
    FAILED_STAGE = ''

    def CONFIG = readJsonConfigFile()
    def choiceEnv = CONFIG["jenkins-config"]["env"]
    def defaultBranch = CONFIG["jenkins-config"]["defaultBranch"]

    pipeline {
        agent any
        parameters {
            choice (
                name: 'TYPE_DEPLOY',
                choices: ['Build & Deploy', 'Build', 'Deploy',],
                description: 'Type Deploy Job'
            )
            booleanParam (
                name: 'STATUS_DB',
                defaultValue: false,
                description: 'Status Database!'
            )
            booleanParam (
                name: 'UPDATE_DB',
                defaultValue: false,
                description: 'Update Database!'
            )
            booleanParam (
                name: 'DROP_DB',
                defaultValue: false,
                description: 'Drop All Database!'
            )
            choice (
                name: 'PROFILE',
                choices: choiceEnv,
                description: 'Profile running enviroment project!'
            )
            string (
                name:'BRANCH',
                defaultValue: defaultBranch,
                description: 'Branch git'
            )
        }
        
        stages {
            stage('Start Build') {
                parallel {
                    stage("Start Description Build") { 
                        steps {
                            script {
                                // get current date
                                def timeBuild = getCurrentDateTime()
                                // def BUILD_USER = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
                                def BUILD_USER = getCurrentUserInfo().userId
                                // change display build name and description
                                currentBuild.displayName = "#${BUILD_NUMBER}-*${params.TYPE_DEPLOY}*-${PROFILE}-${BRANCH}"
                                currentBuild.description = "#Deploy by ${BUILD_USER}, Type is *${params.TYPE_DEPLOY}* on " + timeBuild

                                // track stage name
                                FAILED_STAGE = "${STAGE_NAME}"
                            }
                        }                   
                    }
                    stage("Start Notify"){
                        steps {
                            script {
                                // track stage name
                                FAILED_STAGE = "${STAGE_NAME}"
                            }
                            sendNotify('STARTED')
                        }
                    }
                }
            }
        }

        post {
            // catch faile error
            failure {
            sendNotify(currentBuild.currentResult,'',FAILED_STAGE)
            }

            // catch aborted 
            aborted {
                sendNotify('ABORTED')
            }
        }
    }
    node {
        try {

            if ("${params.TYPE_DEPLOY}" == "Build"){
                buildSource()
            }
            else if ("${params.TYPE_DEPLOY}" == "Deploy") {
                deploySource()
            }
            else{   
                buildSource()
                deploySource()
            }
        }
        catch (ex) {
            if (!currentBuild.rawBuild.getActions(jenkins.model.InterruptedBuildAction.class).isEmpty()) {
                CURRENTSTATE = "ABORTED"
            }
            else {
                currentBuild.result = 'FAILURE'
                CURRENTSTATE ='FAILURE'
                echo "Caught: ${ex}"
            }
        }
        finally {
            if(CURRENTSTATE != "FAILURE") {
                FAILED_STAGE = ""
            }
            sendNotify(CURRENTSTATE,CHANGELOGMESS,FAILED_STAGE)  
        }
    }
}

/*Build Source*/
def buildSource(){

    // read json file
    def CONFIG = readJsonConfigFile()
    echo "${CONFIG}"

    def job_name = Parameters.instance.JOB_NAME
    def renamed_package = "${job_name}.war"

    // get job json file
    def JOB_CONFIG = CONFIG["${params.PROFILE}"]["job"]["${job_name}"]

    // get git url
    def gitUrl = JOB_CONFIG["git-url"]
    echo "${gitUrl}"

    // folder package build out
    def build_package = JOB_CONFIG["send-file-folder"]

    // origin package name
    def original_package = JOB_CONFIG["original-name-package"]

    // get url db
    def dburl = CONFIG["${params.PROFILE}"]["database"]["url"]

    // get user name and pass
    def username = JOB_CONFIG["user-db"]
    def password = JOB_CONFIG["password-db"]

    // credential and gitURl
    def credentialsIdBbk = CONFIG["${params.PROFILE}"]["credentialsId"]

    // command liquidbase
    def liquibaseCommand = "-Ddburl=${dburl} -Ddbuser=${username} -Ddbpass=${password}"

    // check if app env is stag live then using liquibase command
    def extendLiquibaseCommand = (Parameters.instance.APP_ENV == "stag-live") ? liquibaseCommand : ""

    withEnv(["PATH+MAVEN=${tool name: 'Apache Maven 3.6.0', type: 'maven'}/bin"]) {

        stage('Pull Source Code') {
            FAILED_STAGE = "${STAGE_NAME}"
            git branch: "${params.BRANCH}",
                credentialsId: "${credentialsIdBbk}",
                url: "${gitUrl}"
        }

        stage("Copy application file for ${params.PROFILE}") {
            FAILED_STAGE = "${STAGE_NAME}"
            if(Parameters.instance.APP_ENV == "stag-live") {
                // get data from resource file and override application file
                def data = getApplicationFileReousrce()
                writeFile file: "src/main/resources/application-${params.PROFILE}.yml", text: data
            }
            else{
                Utils.markStageSkippedForConditional("${STAGE_NAME}")
                echo "Skip stage"
            }
        }

        stage('Build package') {
            FAILED_STAGE = "${STAGE_NAME}"
            sh "mvn clean package -DskipTests -P${params.PROFILE}"
        }

        stage("Impact Database And Copy Source To tmp Folder") {
            parallel 'Show Status Database': {
                stage('Show Status Database') {
                    if (params.STATUS_DB == true) {
                        FAILED_STAGE = "${STAGE_NAME}"
                        sh "mvn liquibase:status -P${params.PROFILE} ${extendLiquibaseCommand}"
                    }
                    else{
                        Utils.markStageSkippedForConditional("${STAGE_NAME}")
                        echo "Skip stage"
                    }
                }
            },
            'Update Database': {
                stage('Update Database') {
                    if (params.UPDATE_DB == true) {
                        FAILED_STAGE = "${STAGE_NAME}"
                        sh "mvn liquibase:update -P${params.PROFILE}  ${extendLiquibaseCommand}"
                    }
                    else{
                        Utils.markStageSkippedForConditional("${STAGE_NAME}")
                        echo "Skip stage"
                    }
                }
            },
            'Drop Database': {
                stage('Drop Database') {
                    if (params.DROP_DB == true) {
                        FAILED_STAGE = "${STAGE_NAME}"
                        sh "mvn liquibase:dropAll -P${params.PROFILE} ${extendLiquibaseCommand}"
                    }
                    else{
                        Utils.markStageSkippedForConditional("${STAGE_NAME}")
                        echo "Skip stage"
                    }
                }
            },
            'Copy Source': {
                def tmp_buildSource = getTmpBuildSourceFolder()
                sh "bash -c 'if [ ! -d ${WORKSPACE}/${tmp_buildSource} ]; then mkdir -p ${WORKSPACE}/${tmp_buildSource}; else echo foler exist; fi; '"
                sh "cp -rf ${build_package}/${original_package} ${tmp_buildSource}/${renamed_package}"
            }
         }
    }
}


/* Deploy Source */
def deploySource(){

    // read file JSON
    def CONFIG = readJsonConfigFile()

    def job_name = Parameters.instance.JOB_NAME
    def common_name = Parameters.instance.COMMON_NAME
    def renamed_package = "${job_name}.war"

    // get job json file
    def JOB_CONFIG = CONFIG["${params.PROFILE}"]["job"]["${job_name}"]

    // current running app
    def current_folder = JOB_CONFIG["running-folder"]

    // restart command
    def restart_command = JOB_CONFIG["command-restart"]

    // define list server
    def list_server = []

    // parallel create folder each server
    def create_folder_tasks = [:]

    // parallel send file each server
    def send_file_tasks = [:]

    // parallel copy file each server
    def copy_file_tasks = [:]

    // parallel copy file each server
    def check_api_tasks = [:]

    // creadentialId
    def credentialsIdSv = CONFIG["${params.PROFILE}"]["credentialsIdSv"]

    // folder package build out
    def tmp_build_package = getTmpBuildSourceFolder()

    // get currentTime
    def timeCreateFolder = getCurrentDateTime("dd.MM.yyy")

    // deploy folder
    def deploy_folder = CONFIG["${params.PROFILE}"]["deploy-folder"]["${common_name}"] + "${params.PROFILE}/" + CONFIG["${params.PROFILE}"]["job"]["${job_name}"]["deploy-folder-suffix"] + "." + timeCreateFolder + "-${BUILD_NUMBER}"

    withCredentials([sshUserPrivateKey(credentialsId: "${credentialsIdSv}", keyFileVariable: 'identity', usernameVariable: 'userName')]) {

        // get list server from json
        def rawListServer = CONFIG["${params.PROFILE}"]["servers"]["${common_name}"].toString()

        // pare json to array or map
        def parseListServer = readJSON text: rawListServer

        // add server with serverinfo
        for (castServer in parseListServer) {
            def server = [:]
            server.name = castServer.name
            server.host = castServer.host
            server.allowAnyHosts = castServer.allowAnyHosts
            server.user = userName
            server.identityFile = identity
            list_server.add(server)
        }

        stage("Create Deploy Folder!") {
            FAILED_STAGE = "${STAGE_NAME}"
            for (item in list_server) {
                // for error data in closure when using mutiple thread
                def itemServer = item
                create_folder_tasks["Create folder ${item.name}"] = {
                    sshCommand remote: itemServer, command: "echo Create deploy folder"
                    sshCommand remote: itemServer, command: "mkdir -p ${deploy_folder}"
                }
            }
            // parallel create folder each server
            parallel create_folder_tasks
        }

        stage("Send file to deploy folder") {
            FAILED_STAGE = "${STAGE_NAME}"
            for (item in list_server) {
                def itemServer = item
                send_file_tasks["Send file ${item.name}"] = {
                    sshPut remote: itemServer, from: "${tmp_build_package}/${renamed_package}", into: "${deploy_folder}"
                }
            }
            // parallel send folder each server
            parallel send_file_tasks
        }

        stage("Copy all file and folder and file to current folder") {
            FAILED_STAGE = "${STAGE_NAME}"
            for (item in list_server) {
                def itemServer = item
                copy_file_tasks["Cp file to main dir in ${item.name}"] = {
                    sshCommand remote: itemServer, command: "cp -rf ${deploy_folder}/${renamed_package} ${current_folder}"
                }
            }
            // parallel copy folder each server
            parallel copy_file_tasks
        }

        stage("Check Api and Create changelog") {
            parallel "Check Api is starting ?": {
                stage ("Check API Is Starting ?") {
                    FAILED_STAGE = "${STAGE_NAME}"
                    // check api status
                    def port = CONFIG["${params.PROFILE}"]["job"]["${job_name}"]["port"]

                    def timeout_range = CONFIG["${params.PROFILE}"]["timeout"]

                    for (item in list_server) {

                        // hold item server
                        def itemServer = item

                        // ip server
                        def ipServer = itemServer.host

                        // define script check api
                        // sleep 12s and check api untile it get 200 status code
                        def check_api_input = """
                            #!/bin/bash
                            sleep 12
                            result=0
                            while true;
                            do
                            if [ \$result != 200 ];
                            then
                                result=\$(curl -o /dev/null -s -w "%{http_code}\n" http://${ipServer}:${port}/${job_name}/swagger-ui.html);
                            else
                                break;
                            fi
                            done
                        """

                        // tao file checkApiStatus.sh
                        // chay lenh timeout cho script checkapi
                        // sau khoang thoi gian nao do ma khong get duoc 200 thi api se bi timout voi ma 124 ( ma timeout)
                        writeFile file: "check-statusApi/checkApiStatus-${itemServer.name}.sh", text: check_api_input

                        def run_api_input = """
                            #!/bin/bash
                            timeout \$1 bash check-statusApi/checkApiStatus-${itemServer.name}.sh

                        """

                        // tao file runCheckApiStatus.sh
                        writeFile file: "check-statusApi/runCheckApiStatus-${itemServer.name}.sh", text: run_api_input

                        check_api_tasks["Check API Status on Server ${itemServer.name}"] = {
                            stage("Check API Status on Server ${itemServer.name}") {

                                // get output
                                def output  = sh returnStatus: true, script: "bash check-statusApi/runCheckApiStatus-${itemServer.name}.sh ${timeout_range}"

                                // write output value to file and get it back. Cause cannot using outout variable to compare
                                sh "echo ${output} > check-statusApi/result-${itemServer.name}"
                                def read_out_put = readFile("check-statusApi/result-${itemServer.name}").trim()

                                // if exit code is 124 so app is time out
                                if ("${read_out_put}" == "124") {
                                    catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                                        echo "App is timeout"
                                        sh "exit 1"
                                    }
                                }
                                else{
                                    echo "App is started... Enjoyed"
                                }
                            }
                        }
                    }
                    parallel check_api_tasks
                }
            },
            "Create file LogBuildId": {
                stage ("Create file LogBuildId") {
                    FAILED_STAGE = "${STAGE_NAME}"
                    // folder save file build id when all step above in deploy success
                    def log_build_folder = "logBuild/${params.PROFILE}"
                    sh "bash -c 'if [ ! -d ${WORKSPACE}/${log_build_folder} ]; then mkdir -p ${WORKSPACE}/${log_build_folder}; else echo foler exist; fi; '"

                    // save current build number of this env
                    def input = "{'${params.PROFILE}': ${BUILD_NUMBER}}"
                    writeFile file: "${log_build_folder}/buildId.json", text: input
                }
            },
            "Show Change Log": {
                stage ("Show Change Log") {
                    FAILED_STAGE = "${STAGE_NAME}"
                    def publisher = LastChanges.getLastChangesPublisher "LAST_SUCCESSFUL_BUILD", "SIDE", "LINE", true, true, "", "", "", "", ""
                    publisher.publishLastChanges()
                    def changes = publisher.getLastChanges()
                    for (commit in changes.getCommits()) {
                        def commitInfo = commit.getCommitInfo()
                        echo "${commitInfo.getCommitId()} by ${commitInfo.getCommitterName()} on ${commitInfo.getCommitDate()}: ${commitInfo.getCommitMessage()}"
                        CHANGELOGMESS += "\n *${commitInfo.getCommitterName()}* commited on ${commitInfo.getCommitDate()}: *${commitInfo.getCommitMessage()}*"
                    }
                }
            }
        }
    }
}


// notify slack
def sendNotify(String buildStatus = 'STARTED', String changelog = "", String failedStage = "") {

    def showLog = ''
    // get readJsonFile Object
    def CONFIG = readJsonConfigFile()

    def job_name = Parameters.instance.JOB_NAME
    // slack notify
    def notifyChannel = CONFIG["${params.PROFILE}"]["job"]["${job_name}"]["slack-notify"]


    def BUILD_USER = getCurrentUserInfo().userId

    // get current Date
    def timeBuild = getCurrentDateTime()

    def COLOR_MAP = ['SUCCESS': '#54ff76', 'FAILURE': '#FF0000', 'UNSTABLE': '#fffd7a', 'ABORTED': '#7f8fa4', 'STARTED': '#addbd8']

    // check if current result is SUCCES or UNSTABLE
    if(buildStatus == '' ) {
        buildStatus = currentBuild.currentResult
    }

    // Show Changelog when build Success
    if(buildStatus == 'SUCCESS' && changelog == "") {
        showLog = "\n Change Log: Nothing Change"
    }
    else if (buildStatus == 'SUCCESS' && changelog != "") {
        showLog = "\nChange Log: " + changelog
    }
    else {
        showLog = ''
    }

    if(buildStatus == "FAILURE") {
        showLog += "\n" + "FAILURE STAGE: *" + failedStage + "*"
    }

    def msg = "*${buildStatus}:* Build #${BUILD_NUMBER}-${PROFILE}-${BRANCH} \n Deploy by *${BUILD_USER}*, Type is *${params.TYPE_DEPLOY}* on " + timeBuild + "\n More info at: ${BUILD_URL} ${showLog}"

    notify_task = [
        "Send Notify Slack": {
            slackSend(channel: "${notifyChannel}", color: COLOR_MAP[buildStatus], message: msg)
        },
        "Send Notify Email": {
            sendEmail(buildStatus, showLog, BUILD_USER, timeBuild)
        }
    ]

    parallel notify_task
}


// send email
def sendEmail(String buildStatus = "", String showLog = "", String BUILD_USER = "", String timeBuild = "") {
    // read json file
    def CONFIG = readJsonConfigFile()

    // current UserName and Email
    def umail = getCurrentUserInfo().email
    def fullName = getCurrentUserInfo().fullName
    // list email = ''
    def listEmailTo = "${umail}"
    def listEmailCC =  CONFIG["email"]["cc"]
    def listEmailBCC = CONFIG["email"]["bcc"]

    echo "Send Mail ${buildStatus}"

    mail (to: "${listEmailTo}",
          cc: "${listEmailCC}",
          bcc: "${listEmailBCC}",
          subject: "${buildStatus}: Build Job ${JOB_NAME}#${BUILD_NUMBER}-${PROFILE}-${BRANCH}",
          body: "Deploy by ${fullName}, Type is ${params.TYPE_DEPLOY} on " + timeBuild + "\n More info at: ${BUILD_URL} ${showLog}"
    )
}


// read file Json Config
def readJsonConfigFile() {
    // load file json resource
    def path_config = Parameters.instance.PATH_SYS_GLOBAL_FILE + "/config." + Parameters.instance.APP_ENV + ".json"
    def fileResources = libraryResource "${path_config}"

    // parese string to json
    def jsonFile = readJSON text: fileResources
    return jsonFile
}


// get current datetime system Build
def getCurrentDateTime(String typeFormatDate = "EEEE, MMM d, yyyy, h:mm:ss a") {

    def formatDate = new SimpleDateFormat(typeFormatDate, Locale.getDefault())
    formatDate.setTimeZone(TimeZone.getDefault())
    def date = new Date()
    def timeFormat = formatDate.format(date)
    return timeFormat
}


// get current build User
// install plugin user build vars
def getCurrentUserInfo() {
    def userCurrent = [:]
    wrap([$class: 'BuildUser']) {
        userCurrent.fullName = "${BUILD_USER}"
        userCurrent.firstName = "${BUILD_USER_FIRST_NAME}"
        userCurrent.userId = "${BUILD_USER_ID}"
        userCurrent.email = "${BUILD_USER_EMAIL}"
    }
    return userCurrent
}


// get tmpSourcefolder
def getTmpBuildSourceFolder() {
    def tmp_folder = "tmp/${params.PROFILE}"
    return tmp_folder
}



def getApplicationFileReousrce() {
    def path_resource = Parameters.instance.PATH_SYS_APPSETTING_API + "/" + Parameters.instance.JOB_NAME + "/application-${params.PROFILE}.yml"
    def applicationFile =  libraryResource "${path_resource}"
    return applicationFile
}