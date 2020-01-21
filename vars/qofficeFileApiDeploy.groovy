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

    // read file JSON
    def CONFIG = readJsonConfigFile()
    def job_name = Parameters.instance.JOB_NAME
    // get job json file
    def JOB_CONFIG = CONFIG["${params.PROFILE}"]["job"]["${job_name}"]

    // folder name contain source code
    def build_package = JOB_CONFIG["send-file-folder"]

    // get git url of job build
    def gitUrl = JOB_CONFIG["git-url"]

    // get folder source code
    def folder_source_code = JOB_CONFIG["folder-source-code"]

    // get solution name
    def solution_name = JOB_CONFIG["solution-name"]

    // credential and gitURl
    def credentialsIdBbk = CONFIG["${params.PROFILE}"]["credentialsId"]

    stage('Pull Source Code') {
        FAILED_STAGE = "${STAGE_NAME}"
        checkout([$class: 'GitSCM', 
                branches: [[name: "*/${params.BRANCH}"]], 
                extensions: [[$class: 'SubmoduleOption', 
                            recursiveSubmodules: true,
                            trackingSubmodules: true]],
                userRemoteConfigs: [[credentialsId: "${credentialsIdBbk}", 
                                     url: "${gitUrl}"]]])
    }

    stage("Check folder and Copy file env"){
        parallel 'Check if folder container source is exist': {
            stage('Check if folder container source is exist') {
                sh "bash -c 'if [ -d ${WORKSPACE}/${folder_source_code}/${build_package} ]; then rm -rf ${WORKSPACE}/${folder_source_code}/${build_package}; fi;'"
            }
        },
        'Copy file env to main source': {
            stage('Copy file env to main source'){
                def data = getApplicationFileReousrce()
                writeFile file: "${WORKSPACE}/${folder_source_code}/appsettings.json", text: data
            }
        }
    }

    stage('Restore project') {
        FAILED_STAGE = "${STAGE_NAME}"
        sh "dotnet restore ${folder_source_code}/${solution_name}"
    }

    stage('Publish project') {
        FAILED_STAGE = "${STAGE_NAME}"
        sh "dotnet publish ${folder_source_code}/${solution_name} -c Release -o ${build_package}"
    }

    stage("Copy build source to tmp folder") {
        def tmp_buildSource = getTmpBuildSourceFolder()
        // delete folder tmp
        sh "rm -rf ${WORKSPACE}/${tmp_buildSource}"
        sh "mkdir -p ${WORKSPACE}/${tmp_buildSource}"
        sh "cp -rf ${folder_source_code}/${build_package}/* ${tmp_buildSource}"
    }
}

/* Deploy Source */
def deploySource(){
 
    // read file JSON
    def CONFIG = readJsonConfigFile()

    def job_name = Parameters.instance.JOB_NAME
    def common_name = Parameters.instance.COMMON_NAME

    // get job json file
    def JOB_CONFIG = CONFIG["${params.PROFILE}"]["job"]["${job_name}"]

        // current running app
    def current_folder = JOB_CONFIG["running-folder"]

    // restart command
    def restart_command = JOB_CONFIG["command-restart"]

    // get currentTime
    def timeCreateFolder = getCurrentDateTime("dd.MM.yyy")

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

    /* GET CREDENTIAL */
    // creadentialId    
    def credentialsIdSv = CONFIG["${params.PROFILE}"]["credentialsIdSv"]

    // folder package build out
    def tmp_build_package = getTmpBuildSourceFolder()
    
    // deploy folder
    def deploy_folder = CONFIG["${params.PROFILE}"]["deploy-folder"]["${common_name}"] + "${params.PROFILE}/" + JOB_CONFIG["deploy-folder-suffix"] + "." + timeCreateFolder + "-${BUILD_NUMBER}"

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
                create_folder_tasks["Create folder ${itemServer.name}"] = {
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
                send_file_tasks["Send file ${itemServer.name}"] = {
                    sshPut remote: itemServer, from: "${tmp_build_package}", into: "${deploy_folder}"
                }
            }
            // parallel send folder each server
            parallel send_file_tasks
        }
        
        

        stage("Copy all file and folder and file to current folder") {
            FAILED_STAGE = "${STAGE_NAME}"
            for (item in list_server) {
                def itemServer = item
                copy_file_tasks["Cp file to main dir in ${itemServer.name}"] = {
                    sshCommand remote: itemServer, command: "cp -rf ${deploy_folder}/${params.PROFILE}/* ${current_folder}"
                }
            }        
            // parallel copy folder each server
            parallel copy_file_tasks
        }

        stage("Restart File Api"){
            FAILED_STAGE = "${STAGE_NAME}"
            for (item in list_server) { 
                for(itemCommand in restart_command){
                    sshCommand remote: item, command: "${itemCommand.command}"
                }
            }
        }

        stage("Create changelog") {
            parallel "Create file LogBuildId": {
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
                    def count = 0
                    for (commit in changes.getCommits()) {
                        if(count >= 10){
                            break
                        }
                        def commitInfo = commit.getCommitInfo()
                        echo "${commitInfo.getCommitId()} by ${commitInfo.getCommitterName()} on ${commitInfo.getCommitDate()}: ${commitInfo.getCommitMessage()}"
                        CHANGELOGMESS += "\n *${commitInfo.getCommitterName()}* commited on ${commitInfo.getCommitDate()}: *${commitInfo.getCommitMessage()}*"
                        count += 1
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
    def path_resource = Parameters.instance.PATH_SYS_APPSETTING_FILE_API + "/appsettings.json.${params.PROFILE}"
    def applicationFile =  libraryResource "${path_resource}"
    return applicationFile
}