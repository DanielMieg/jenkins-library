package cmd

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestCloudFoundryDeleteService2(t *testing.T) {
	s := shellMockRunner{}
	t.Run("CF Login: success case", func(t *testing.T) {
		config := cloudFoundryDeleteServiceOptions{
			API:          "https://api.endpoint.com",
			Organisation: "testOrg",
			Space:        "testSpace",
			Username:     "testUser",
			Password:     "testPassword",
		}
		error := cloudFoundryLogin(config.API, config.Organisation, config.Space, config.Username, config.Password, &s)
		if error == nil {
			assert.Equal(t, "cf login -a https://api.endpoint.com -o testOrg -s testSpace -u testUser -p testPassword", s.calls[0])
		}
	})
	t.Run("CF Delete Service: Success case", func(t *testing.T) {
		ServiceName := "testInstance"
		error := cloudFoundryDeleteServiceFunction(ServiceName, &s)
		if error == nil {
			assert.Equal(t, "cf delete-service testInstance -f", s.calls[1])
		}
	})
	t.Run("CF Logout: Success case", func(t *testing.T) {
		error := cloudFoundryLogout(&s)
		if error == nil {
			assert.Equal(t, "cf logout", s.calls[2])
		}
	})
}
