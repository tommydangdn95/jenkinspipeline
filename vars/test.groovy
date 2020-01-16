import groovy.json.JsonSlurper

def call() {
    pipeline {
        agent any
        stages{
            stage("Hello"){
                steps {
                    echo "Hello"
                }
            }
        }
    }
    node {
        stage("Test load Result") {
            def CONFIG = showJson()
            echo "Show call method"
            def servers = CONFIG['stag']['job']['migrated']
            echo "${servers}"
            // echo "Change file application"
            // echo "Write file"
            // writeFile file: "src/main/resources/application-dev.yml", text: data
        }
    }
}

// def changeFileApplication() {
//     def applicationFile =  libraryResource 'vn/tcx/application/danh-muc/application-live.yml'
//     return applicationFile
// }

def showJson() {
    def fileResources = libraryResource 'vn/tcx/stag-live/config.json'
    echo "${fileResources}"
    def jsonFile = readJSON text: fileResources
    return jsonFile
}