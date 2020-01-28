package cmd

import (
	"bytes"
	"encoding/json"
	"io/ioutil"
	"net/http"
	"net/http/cookiejar"
	"time"

	"github.com/SAP/jenkins-library/pkg/command"
	"github.com/SAP/jenkins-library/pkg/log"
	"github.com/pkg/errors"
)

func abapEnvironmentPullGitRepo(config abapEnvironmentPullGitRepoOptions) error {
	c := command.Command{}
	cookieJar, _ := cookiejar.New(nil)
	client := &http.Client{
		Jar: cookieJar,
	}

	var connectionDetails, error = getAbapCommunicationArrangementInfo(config, &c)
	if error != nil {
		log.Entry().WithError(error).Fatal("Parameters for the ABAP Connection not available")
		return error
	}

	var uriConnectionDetails, err = triggerPull(config, connectionDetails, client)
	if err != nil {
		log.Entry().WithError(err).Fatal("Pull failed on the ABAP System")
		return err
	}

	var status, er = pollEntity(config, uriConnectionDetails, client, 10*time.Second)
	if status == "E" || err != nil {
		log.Entry().WithError(er).Fatal("Pull failed on the ABAP System")
		return err
	}

	return nil
}

func pollEntity(config abapEnvironmentPullGitRepoOptions, connectionDetails connectionDetailsHTTP, client httpClient, pollIntervall time.Duration) (string, error) {

	log.Entry().Info("Start polling the status...")
	var status string = "R"

	for {
		var resp, err = getHTTPResponse("GET", connectionDetails, nil, client)
		defer resp.Body.Close()
		if err != nil {
			log.Entry().WithField("StatusCode", resp.Status).WithField("repositoryName", config.RepositoryName).Error("Could not pull the Repository / Software Component")
			return "", err
		}

		var body abapEntity
		bodyText, _ := ioutil.ReadAll(resp.Body)
		var abapResp map[string]*json.RawMessage
		json.Unmarshal(bodyText, &abapResp)
		json.Unmarshal(*abapResp["d"], &body)
		if body == (abapEntity{}) {
			log.Entry().WithField("StatusCode", resp.Status).WithField("repositoryName", config.RepositoryName).Error("Could not pull the Repository / Software Component")
			var err = errors.New("Request to ABAP System not successful")
			return "", err
		}
		status = body.Status
		log.Entry().WithField("StatusCode", resp.Status).Info("Pull Status: " + body.StatusDescr)
		if body.Status != "R" {
			break
		}
		time.Sleep(pollIntervall)
	}

	return status, nil
}

func triggerPull(config abapEnvironmentPullGitRepoOptions, pullConnectionDetails connectionDetailsHTTP, client httpClient) (connectionDetailsHTTP, error) {

	uriConnectionDetails := pullConnectionDetails
	uriConnectionDetails.URL = ""
	pullConnectionDetails.XCsrfToken = "fetch"

	// Loging into the ABAP System - getting the x-csrf-token and cookies
	log.Entry().WithField("ABAP Endpoint", pullConnectionDetails.URL).Info("Calling the ABAP System...")
	log.Entry().Info("Trying to authenticate on the ABAP system...")

	var resp, err = getHTTPResponse("HEAD", pullConnectionDetails, nil, client)
	defer resp.Body.Close()
	if err != nil {
		log.Entry().WithField("StatusCode", resp.Status).Error("Authentication failed")
		return uriConnectionDetails, err
	}
	log.Entry().WithField("StatusCode", resp.Status).Info("Authentication successfull")
	uriConnectionDetails.XCsrfToken = resp.Header.Get("X-Csrf-Token")
	pullConnectionDetails.XCsrfToken = uriConnectionDetails.XCsrfToken

	// Trigger the Pull of a Repository
	var jsonBody = []byte(`{"sc_name":"` + config.RepositoryName + `"}`)
	log.Entry().WithField("repositoryName", config.RepositoryName).Info("Pulling Repository / Software Component")

	resp, err = getHTTPResponse("POST", pullConnectionDetails, jsonBody, client)
	defer resp.Body.Close()
	if err != nil {
		log.Entry().WithField("StatusCode", resp.Status).WithField("repositoryName", config.RepositoryName).Error("Could not pull the Repository / Software Component")
		return uriConnectionDetails, err
	}
	log.Entry().WithField("StatusCode", resp.Status).WithField("repositoryName", config.RepositoryName).Info("Triggered Pull of Repository / Software Component")

	// Parse Response
	var body abapEntity
	var abapResp map[string]*json.RawMessage
	bodyText, err := ioutil.ReadAll(resp.Body)
	json.Unmarshal(bodyText, &abapResp)
	json.Unmarshal(*abapResp["d"], &body)
	if body == (abapEntity{}) {
		log.Entry().WithField("StatusCode", resp.Status).WithField("repositoryName", config.RepositoryName).Error("Could not pull the Repository / Software Component")
		var err = errors.New("Request to ABAP System not successful")
		return uriConnectionDetails, err
	}
	uriConnectionDetails.URL = body.Metadata.URI
	return uriConnectionDetails, nil
}

func getAbapCommunicationArrangementInfo(config abapEnvironmentPullGitRepoOptions, c shellRunner) (connectionDetailsHTTP, error) {

	var connectionDetails connectionDetailsHTTP
	var error error

	if config.Host != "" {
		// Host, User and Password are directly provided
		connectionDetails.URL = "https://" + config.Host + "/sap/opu/odata/sap/MANAGE_GIT_REPOSITORY/Pull"
		connectionDetails.User = config.User
		connectionDetails.Password = config.Password
	} else {
		if config.CfAPIEndpoint == "" || config.CfOrg == "" || config.CfSpace == "" || config.CfServiceInstance == "" || config.CfServiceKey == "" {
			var err = errors.New("Parameters missing. Please provide EITHER the Host of the ABAP server OR the Cloud Foundry ApiEndpoint, Organization, Space, Service Instance and a corresponding Service Key for the Communication Scenario SAP_COM_0510")
			return connectionDetails, err
		}
		// Url, User and Password should be read from a cf service key
		var abapServiceKey, error = readCfServiceKey(config, c)
		if error != nil {
			log.Entry().Error(error)
			return connectionDetails, error
		}
		connectionDetails.URL = abapServiceKey.URL + "/sap/opu/odata/sap/MANAGE_GIT_REPOSITORY/Pull"
		connectionDetails.User = abapServiceKey.Abap.Username
		connectionDetails.Password = abapServiceKey.Abap.Password
	}
	return connectionDetails, error
}

func readCfServiceKey(config abapEnvironmentPullGitRepoOptions, c shellRunner) (serviceKey, error) {

	var abapServiceKey serviceKey

	c.Stderr(log.Entry().Writer())
	c.Stdout(log.Entry().Writer())

	// Logging into the Cloud Foundry via CF CLI
	log.Entry().WithField("cfApiEndpoint", config.CfAPIEndpoint).WithField("cfSpace", config.CfSpace).WithField("cfOrg", config.CfOrg).WithField("User", config.User).Info("Cloud Foundry parameters: ")
	var cfLoginScript = "cf login -a " + config.CfAPIEndpoint + " -u " + config.User + " -p " + config.Password + " -o " + config.CfOrg + " -s " + config.CfSpace
	error := c.RunShell("/bin/bash", cfLoginScript)
	// cflogin, error := exec.Command("sh", "-c", cfLoginScript).Output()
	if error != nil {
		log.Entry().Error("Login at cloud foundry failed.")
		return abapServiceKey, error
	}

	var serviceKeyBytes bytes.Buffer
	c.Stdout(&serviceKeyBytes)

	// Reading the Service Key via CF CLI
	log.Entry().WithField("cfServiceInstance", config.CfServiceInstance).WithField("cfServiceKey", config.CfServiceKey).Info("Reading service key of service instance...")
	var cfReadServiceKeyScript = "cf service-key " + config.CfServiceInstance + " " + config.CfServiceKey + " | awk '{if(NR>1)print}'"
	error = c.RunShell("/bin/bash", cfReadServiceKeyScript)
	if error != nil {
		log.Entry().Error("Reading the service key failed.")
		return abapServiceKey, error
	}

	json.Unmarshal(serviceKeyBytes.Bytes(), &abapServiceKey)
	return abapServiceKey, error
}

func getHTTPResponse(requestType string, connectionDetails connectionDetailsHTTP, body []byte, client httpClient) (*http.Response, error) {

	req, _ := http.NewRequest(requestType, connectionDetails.URL, bytes.NewBuffer(body))
	req.Header.Add("x-csrf-token", connectionDetails.XCsrfToken)
	req.Header.Add("Content-Type", "application/json")
	req.Header.Add("Accept", "application/json")
	req.SetBasicAuth(connectionDetails.User, connectionDetails.Password)
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}

	if resp.StatusCode >= 300 {
		log.Entry().WithField("StatusCode", resp.Status).Error("Request to ABAP System failed")
		err = errors.New("Request to ABAP System failed")
	}

	return resp, err
}

type httpClient interface {
	Do(req *http.Request) (*http.Response, error)
}

type abapEntity struct {
	Metadata       abapMetadata `json:"__metadata"`
	UUID           string
	ScName         string `json:"sc_name"`
	Namespace      string
	Status         string
	StatusDescr    string   `json:"status_descr"`
	ToExecutionLog deferred `json:"to_Execution_log"`
	ToTransportLog deferred `json:"to_Transport_log"`
}

type abapMetadata struct {
	URI string
}

type serviceKey struct {
	Abap     abapConenction
	Binding  abapBinding
	Systemid string
	URL      string
}

type deferred struct {
	URI string
}

type abapConenction struct {
	CommunicationArrangementID string `json:"communication_arrangement_id"`
	CommunicationScenarioID    string `json:"communication_scenario_id"`
	CommunicationSystemID      string `json:"communication_system_id"`
	Password                   string
	Username                   string
}

type abapBinding struct {
	Env     string
	ID      string
	Type    string
	Tersion string
}

type connectionDetailsHTTP struct {
	User       string
	Password   string
	URL        string
	XCsrfToken string
}
