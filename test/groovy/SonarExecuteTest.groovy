import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.allOf

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.ExpectedException
import static org.junit.Assert.assertThat

import util.BasePiperTest
import util.JenkinsDockerExecuteRule
import util.JenkinsShellCallRule
import util.JenkinsReadYamlRule
import util.JenkinsStepRule
import util.JenkinsLoggingRule
import util.Rules

class SonarExecuteScanTest extends BasePiperTest {
    private ExpectedException thrown = ExpectedException.none()
    private JenkinsReadYamlRule readYamlRule = new JenkinsReadYamlRule(this)
    private JenkinsStepRule jsr = new JenkinsStepRule(this)
    private JenkinsLoggingRule jlr = new JenkinsLoggingRule(this)
    private JenkinsShellCallRule jscr = new JenkinsShellCallRule(this)
    private JenkinsDockerExecuteRule jedr = new JenkinsDockerExecuteRule(this)

    @Rule
    public RuleChain rules = Rules
        .getCommonRules(this)
        .around(readYamlRule)
        .around(thrown)
        .around(jedr)
        .around(jscr)
        .around(jlr)
        .around(jsr)

    def sonarInstance

    @Before
    void init() throws Exception {
        sonarInstance = null
        helper.registerAllowedMethod("withSonarQubeEnv", [String.class, Closure.class], { string, closure ->
            sonarInstance = string
            return closure()
        })
        helper.registerAllowedMethod("unstash", [String.class], { stashInput -> return []})
        helper.registerAllowedMethod("fileExists", [String.class], { file -> return file })
        helper.registerAllowedMethod('string', [Map], { m -> m })
        helper.registerAllowedMethod('withCredentials', [List, Closure], { l, c ->
            try {
                binding.setProperty(l[0].variable, 'TOKEN_'+l[0].credentialsId)
                c()
            } finally {
                binding.setProperty(l[0].variable, null)
            }
        })
        nullScript.commonPipelineEnvironment.setArtifactVersion('1.2.3-20180101')
    }

    @Test
    void testWithDefaults() throws Exception {
        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils
        )

        // asserts
        assertThat('Sonar instance is not set to the default value', sonarInstance, is('SonarCloud'))
        assertThat('Sonar project version is not set to the default value', jscr.shell, hasItem(containsString('sonar-scanner -Dsonar.projectVersion=1')))
        assertThat('Docker image is not set to the default value', jedr.dockerParams.dockerImage, is('maven:3.5-jdk-8'))
        assertJobStatusSuccess()
    }

    @Test
    void testWithOptionals() throws Exception {
        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils,
            organization: 'TestOrg-github'
        )

        // asserts
        assertThat(jscr.shell, hasItem(containsString('-Dsonar.organization=TestOrg-github')))
        assertJobStatusSuccess()
    }

    @Test
    void testWithCustomVersion() throws Exception {
        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils,
            projectVersion: '2'
        )

        // asserts
        assertThat('Sonar project version is not set to the custom value', jscr.shell, hasItem(containsString('sonar-scanner -Dsonar.projectVersion=2')))
        assertJobStatusSuccess()
    }

    @Test
    void testWithCustomOptions() throws Exception {
        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils,
            options: '-DmyCustomSettings'
        )

        // asserts
        assertThat('Sonar options are not set to the custom value', jscr.shell, hasItem(containsString('sonar-scanner -DmyCustomSettings')))
        assertJobStatusSuccess()
    }

    @Test
    void testWithCustomOptionsList() throws Exception {
        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils,
            options: ['myCustomSettings', 'sonar.host.url=localhost']
        )

        // asserts
        assertThat('Sonar options are not set to the custom value', jscr.shell, hasItem(containsString('sonar-scanner -DmyCustomSettings -Dsonar.host.url=localhost')))
        assertJobStatusSuccess()
    }

    @Test
    void testWithCustomInstance() throws Exception {
        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils,
            instance: 'MySonarInstance'
        )

        // asserts
        assertThat('Sonar instance is not set to the custom value', sonarInstance.toString(), is('MySonarInstance'))
        assertJobStatusSuccess()
    }
/*
    @Test
    void testWithEmptyProjectVersion() throws Exception {
        //thrown.expect(Exception)
        //thrown.expectMessage('ERROR - NO VALUE AVAILABLE FOR projectVersion')

        nullScript.commonPipelineEnvironment.setArtifactVersion(null)
        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils
        )

        // asserts
        assertJobStatusFailure()
    }
*//*
    @Test
    void testWithClosure() throws Exception {
        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils
        ){
            nullScript.echo "in closure"
        }

        // asserts
        assertThat(jlr.log, containsString('in closure'))
        assertJobStatusSuccess()
    }
*/
    @Test
    void testWithGithubAuth() throws Exception {
        binding.setVariable('env', ['CHANGE_ID': '42'])

        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils,
            legacyPRHandling: true,
            githubTokenCredentialsId: 'githubId',
            githubOrg: 'testOrg',
            githubRepo: 'testRepo'
        )
        // asserts
        assertThat(jscr.shell, hasItem(allOf(
            containsString('-Dsonar.analysis.mode=preview'),
            containsString('-Dsonar.github.pullRequest=42'),
            containsString('-Dsonar.github.oauth=TOKEN_githubId'),
            containsString('-Dsonar.github.repository=testOrg/testRepo')
        )))
        assertJobStatusSuccess()
    }

    @Test
    void testWithSonarAuth() throws Exception {
        jsr.step.sonarExecuteScan(
            script: nullScript,
            juStabUtils: utils,
            sonarTokenCredentialsId: 'githubId'
        )
        // asserts
        assertThat(jscr.shell, hasItem(
            containsString('-Dsonar.login=TOKEN_githubId')
        ))
        assertJobStatusSuccess()
    }
}
