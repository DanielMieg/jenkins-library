package cmd

import (
	"github.com/SAP/jenkins-library/pkg/command"
	"github.com/SAP/jenkins-library/pkg/log"
)

func cloudFoundryDeleteService(CloudFoundryDeleteServiceOptions cloudFoundryDeleteServiceOptions) error {

	c := command.Command{}

	// reroute command output to logging framework
	c.Stdout(log.Entry().Writer())
	c.Stderr(log.Entry().Writer())

	cloudFoundryLogin(CloudFoundryDeleteServiceOptions, &c)
	cloudFoundryDeleteServiceFunction(CloudFoundryDeleteServiceOptions.CfServiceInstance, &c)
	cloudFoundryLogout(&c)

	return nil
}

func cloudFoundryLogin(CloudFoundryDeleteServiceOptions cloudFoundryDeleteServiceOptions, c execRunner) error {
	var cfLoginScript = []string{"login", "-a", CloudFoundryDeleteServiceOptions.CfAPIEndpoint, "-o", CloudFoundryDeleteServiceOptions.CfOrg, "-s", CloudFoundryDeleteServiceOptions.CfSpace, "-u", CloudFoundryDeleteServiceOptions.Username, "-p", CloudFoundryDeleteServiceOptions.Password}

	log.Entry().WithField("cfAPI:", CloudFoundryDeleteServiceOptions.CfAPIEndpoint).WithField("cfOrg", CloudFoundryDeleteServiceOptions.CfOrg).WithField("space", CloudFoundryDeleteServiceOptions.CfSpace).WithField("password", CloudFoundryDeleteServiceOptions.Password).Info("Logging into Cloud Foundry..")

	err := c.RunExecutable("cf", cfLoginScript...)
	if err != nil {
		log.Entry().
			WithError(err).
			Fatal("Failed to login to Cloud Foundry")
	}
	log.Entry().Info("Logged in successfully to Cloud Foundry..")
	return err
}

func cloudFoundryDeleteServiceFunction(service string, c execRunner) error {
	var cfdeleteServiceScript = []string{"delete-service", service, "-f"}

	log.Entry().WithField("cfService", service).Info("Deleting the requested Service")

	err := c.RunExecutable("cf", cfdeleteServiceScript...)
	if err != nil {
		log.Entry().
			WithError(err).
			Fatal("Failed to delete Service")
	}
	log.Entry().Info("Deletion of Service is finished or Service has never existed before, thus can't need to be deleted")
	return err
}

func cloudFoundryLogout(c execRunner) error {
	var cfLogoutScript = "logout"

	log.Entry().Info("Logging out of Cloud Foundry")

	err := c.RunExecutable("cf", cfLogoutScript)
	if err != nil {
		log.Entry().
			WithError(err).
			Fatal("Failed to Logout of Cloud Foudnry")
	}
	log.Entry().Info("Logged out successfully")
	return err
}
