import static com.sap.piper.Prerequisites.checkScript
import com.sap.piper.ConfigurationHelper
import com.sap.piper.GenerateDocumentation
import com.sap.piper.JenkinsUtils
import com.sap.piper.Utils
import groovy.json.JsonSlurper
import hudson.AbortException
import groovy.transform.Field
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

@Field def STEP_NAME = getClass().getName()
@Field Set GENERAL_CONFIG_KEYS = [
    /**
     * Specifies the host address
     */
    'host',
    /**
     * Specifies the name of the Software Component
     */
    'repositoryName',
    /**
     * Specifies the communication user of the communication scenario SAP_COM_0510
     */
    'username',
    /**
     * Specifies the password of the communication user
     */
    'password']
@Field Set STEP_CONFIG_KEYS = GENERAL_CONFIG_KEYS
@Field Set PARAMETER_KEYS = STEP_CONFIG_KEYS
/**
 * Pulls a Software Component to a SAP Cloud Platform ABAP Environment System.
 *
 * Prerequisite: the Communication Arrangement for the Communication Scenario SAP_COM_0510 has to be set up, including a Communication System and Communication Arrangement
 */
@GenerateDocumentation
void call(Map parameters = [:]) {

    handlePipelineStepErrors(stepName: STEP_NAME, stepParameters: parameters, failOnError: true) {

        def script = checkScript(this, parameters) ?: this

        ConfigurationHelper configHelper = ConfigurationHelper.newInstance(this)
            .mixinGeneralConfig(script.commonPipelineEnvironment, GENERAL_CONFIG_KEYS)
            .mixinStepConfig(script.commonPipelineEnvironment, STEP_CONFIG_KEYS)
            .mixin(parameters, PARAMETER_KEYS)

        Map configuration = configHelper.use()

        configHelper
            .withMandatoryProperty('host')
            .withMandatoryProperty('repositoryName')
            .withMandatoryProperty('username')
            .withMandatoryProperty('password')

        String usernameColonPassword = configuration.username + ":" + configuration.password
        String authToken = usernameColonPassword.bytes.encodeBase64().toString()
        String urlString = configuration.host + ':443/sap/opu/odata/sap/MANAGE_GIT_REPOSITORY/Pull'
        echo "[${STEP_NAME}] General Parameters: URL = \"${urlString}\", repositoryName = \"${configuration.repositoryName}\""

        def url = new URL(urlString)
        Map tokenAndCookie = getXCsrfTokenAndCookie(url, authToken)
        HttpURLConnection connection = createPostConnection(url, tokenAndCookie.token, tokenAndCookie.cookie, authToken)
        connection.connect()
        OutputStream outputStream = connection.getOutputStream()
        String input = '{ "sc_name" : "' + configuration.repositoryName + '" }'
        outputStream.write(input.getBytes())
        outputStream.flush()

        int statusCode = connection.responseCode

        if (!(statusCode == 200 || statusCode == 201)) {
            error "[${STEP_NAME}] Error: ${connection.getErrorStream().text}"
            connection.disconnect()
            throw new Exception("HTTPS Connection Failed")
        }

        JsonSlurper slurper = new JsonSlurper()
        Map object = slurper.parseText(connection.content.text)
        connection.disconnect()
        String pollUri = object.d."__metadata"."uri"
        def pollUrl = new URL(pollUri)

        echo "[${STEP_NAME}] Pull Entity: ${pollUri}"
        echo "[${STEP_NAME}] Pull Status: ${object.d."status_descr"}"

        String status = object.d."status"
        String statusText = object.d."status_descr"

        while(status == 'R') {

            Thread.sleep(5000)
            HttpURLConnection pollConnection = createDefaultConnection(pollUrl, authToken)
            pollConnection.connect()

            if (pollConnection.responseCode == 200 || pollConnection.responseCode == 201) {

                Map pollObject = slurper.parseText(pollConnection.content.text)
                statusText = pollObject.d."status_descr"
                status = pollObject.d."status"
                pollConnection.disconnect()

            } else {

                error "[${STEP_NAME}] Error: ${pollConnection.getErrorStream().text}"
                pollConnection.disconnect()
                throw new Exception("HTTPS Connection Failed")
            }
        }

        echo "[${STEP_NAME}] Pull Status: ${statusText}"
        if (status != 'S') {
            throw new Exception("Pull Failed")
        }  
    }
}


def Map getXCsrfTokenAndCookie(URL url, String authToken) {

    HttpURLConnection connection = createDefaultConnection(url, authToken)
    connection.setRequestProperty("x-csrf-token", "fetch")

    connection.setRequestMethod("GET")
    connection.connect()
    token =  connection.getHeaderField("x-csrf-token")
    cookie1 = connection.getHeaderField(1).split(";")[0] 
    cookie2 = connection.getHeaderField(2).split(";")[0] 
    cookie = cookie1 + "; " + cookie2 
    connection.disconnect()
    connection = null

    Map result = [:]
    result.cookie = cookie
    result.token = token
    return result

}

def HttpURLConnection createDefaultConnection(URL url, String authToken) {

    HttpURLConnection connection = (HttpURLConnection) url.openConnection()
    connection.setRequestProperty("Authorization", "Basic " + authToken)
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/json")
    return connection

}

def HttpURLConnection createPostConnection(URL url, String token, String cookie, String authToken) {

    HttpURLConnection connection = createDefaultConnection(url, authToken)
    connection.setRequestProperty("cookie", cookie)
    connection.setRequestProperty("x-csrf-token", token)
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    connection.setDoInput(true)
    return connection

}