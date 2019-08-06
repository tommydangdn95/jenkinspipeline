import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import hudson.model.User;
import hudson.tasks.Mailer;

// list server
def list_server = []

def remote218 = [:]
remote218.name = "eoffqn-218"
remote218.host = "192.168.1.218"
remote218.allowAnyHosts = true
list_server.add(remote218)

def remote219 = [:]
remote219.name = "eoffqn-219"
remote219.host = "192.168.1.219"
remote219.allowAnyHosts = true
list_server.add(remote219)

def remote233 = [:]
remote233.name = "eoffqn-233"
remote233.host = "192.168.1.233"
remote233.allowAnyHosts = true
list_server.add(remote233)


def remote220 = [:]
remote220.name = "eoffqn-220"
remote220.host = "192.168.1.220"
remote220.allowAnyHosts = true

list_server.add(remote220)

// end list server

def date = new Date()
def sdf = new SimpleDateFormat("EEEE, MMM d, yyyy, h:mm:ss a", Locale.getDefault())
sdf.setTimeZone(TimeZone.getDefault())

// current state build
def CURRENTSTATE = ''

// get user build
def BUILD_USER = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()

// changelog message
CHANGELOGMESS = ''

// faile stage
FAILED_STAGE = ''

pipeline {
	agent any

	parameters {
		choice (
			name: 'PROFILE',
			choices: ['dev', 'prod'],
			description: 'Profile running enviroment project!'
			)
		string (
			name:'BRANCH',
			defaultValue: 'develop',
			description: 'branch git'
			)
	}
	tools {
		nodejs 'nodejs 11.14.0' 
	}

	stages {
        stage('Pull Source Code') {
            steps {
                script {
                    FAILED_STAGE = "${STAGE_NAME}"
                }
                git branch: "${params.BRANCH}",
                credentialsId: '3a9adfa3-55f3-45e4-ad77-370ac97777b6',
                url: 'git@bitbucket.org:toancauxanh/eoffice-qn-deploy-config.git'
                }
            }

        stage('Echo ENV') {
                steps {
                    sh "echo ${PROFILE} ${BRANCH}"
                }
        }
        
        }
   }


node {
    stage("Show Changlog ( Plugin gitchngelog) ") {
        def changelogContext = gitChangelog returnType: 'CONTEXT',
        from: [type: 'REF', value: "master"],
        to: [type: 'REF', value: "${BRANCH}"]

        changelogContext.commits.each { commit ->
        echo "Commit id: ${commit.messageTitle}  ${commit.messageBody}  ${commit.messageBodyItems}" }
    }

    stage("Show Changelog 2") {
        FAILED_STAGE = "${STAGE_NAME}"
        def changeLogSets = currentBuild.changeSets
        for (int i = 0; i < changeLogSets.size(); i++) {
            def entries = changeLogSets[i].items
            for (int j = 0; j < entries.length; j++) {
                def entry = entries[j]
                echo "${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}"
                CHANGELOGMESS+= "\n *${entry.author}* commited on ${new Date(entry.timestamp)}: *${entry.msg}*"
            }
        }
    }
}


// def notifySlack(String buildStatus = 'STARTED', String changelog = "", String failedStage = "") {
//     def showLog = ''
//     def BUILD_USER = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
//     def date = new Date()
//     def sdf = new SimpleDateFormat("EEEE, MMM d, yyyy, h:mm:ss a", Locale.getDefault())
//     sdf.setTimeZone(TimeZone.getDefault())
//     def COLOR_MAP = ['SUCCESS': '#54ff76', 'FAILURE': '#FF0000', 'UNSTABLE': '#fffd7a', 'ABORTED': '#7f8fa4', 'STARTED': '#addbd8']
//     def timeBuild = sdf.format(date);
//     // check if current result is SUCCES or UNSTABLE
//     if(buildStatus == '' ) {
//         buildStatus = currentBuild.currentResult
//     }

//     // Show Changelog when build Success
//     if(buildStatus == 'SUCCESS' && changelog == "") {
//         showLog = "\n Change Log: Nothing Change" 
//     }
//     else if (buildStatus == 'SUCCESS' && changelog != "") {
//         showLog = "\nChange Log: " + changelog
//     }
//     else {
//         showLog = ''
//     }

//     if(buildStatus == "FAILURE") {
//         showLog += "\n" + "FAILURE STAGE: *" + failedStage + "*"
//     }

//     def msg = "*${buildStatus}:* Build #${BUILD_NUMBER}-${PROFILE}-${BRANCH} \n Deploy by *${BUILD_USER}* on " + timeBuild + "\n More info at: ${BUILD_URL} ${showLog}"

//     // send slack
//     slackSend(channel: "#build-fe", color: COLOR_MAP[buildStatus], message: msg)

//     // sendEmail(buildStatus, showLog, BUILD_USER, timeBuild)
// }


// @NonCPS
// def sendEmail(String buildStatus = "", String showLog = "", String BUILD_USER = "", String timeBuild = "") {
//     // current UserName and Email
//     def userCurrent = currentBuild.rawBuild.getCause(Cause.UserIdCause)
//     def fullName = userCurrent.getUserName()
//     User u = User.get(userCurrent.getUserId())
//     def umail = u.getProperty(Mailer.UserProperty.class).getAddress()

//     // list email = ''
//     def listEmailTo = "${umail}"
//     def listEmailCC = 'dtranthuy1995@gmail.com'
//     def listEmailBCC = 'jenkinscigg@gmail.com, cedricjgreen@my.smccd.edu'

//     echo "User email: ${umail}"
//     echo "Send Mail ${buildStatus}"
    
//     mail (to: "${listEmailTo}", 
//           cc: "${listEmailCC}", 
//           bcc: "${listEmailBCC}",
//           subject: "${buildStatus}: Build #${BUILD_NUMBER}-${PROFILE}-${BRANCH}", 
//           body: "Deploy by ${fullName} on " + timeBuild + "\n More info at: ${BUILD_URL} ${showLog}"
//     )
// }