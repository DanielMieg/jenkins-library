import com.cloudbees.groovy.cps.NonCPS
import com.sap.piper.ConfigurationHelper
import com.sap.piper.GenerateStageDocumentation
import com.sap.piper.JenkinsUtils
import com.sap.piper.Utils
import groovy.transform.Field

import static com.sap.piper.Prerequisites.checkScript

@Field String STEP_NAME = getClass().getName()

@Field Set GENERAL_CONFIG_KEYS = []
@Field STAGE_STEP_KEYS = []
@Field Set STEP_CONFIG_KEYS = GENERAL_CONFIG_KEYS.plus(STAGE_STEP_KEYS)
@Field Set PARAMETER_KEYS = STEP_CONFIG_KEYS

/**
 * This stage initializes the pipeline run and prepares further execution.
 *
 * It will check out your repository and perform some steps to initialize your pipeline run.
 */
@GenerateStageDocumentation(defaultStageName = 'Init')
void call(Map parameters = [:]) {

    def script = checkScript(this, parameters) ?: this
    def utils = parameters.juStabUtils ?: new Utils()

    def stageName = parameters.stageName?:env.STAGE_NAME

    piperStageWrapper (script: script, stageName: stageName, stashContent: [], ordinal: 1, telemetryDisabled: true) {
        def scmInfo = checkout scm

        setupCommonPipelineEnvironment script: script, customDefaults: parameters.customDefaults

        Map config = ConfigurationHelper.newInstance(this)
            .loadStepDefaults()
            .mixinGeneralConfig(script.commonPipelineEnvironment, GENERAL_CONFIG_KEYS)
            .mixinStageConfig(script.commonPipelineEnvironment, stageName, STEP_CONFIG_KEYS)
            .mixin(parameters, PARAMETER_KEYS)
            .use()

        //perform stashing based on libray resource piper-stash-settings.yml if not configured otherwise
        initStashConfiguration(script, config)

        if (config.verbose) {
            echo "piper-lib-os  configuration: ${script.commonPipelineEnvironment.configuration}"
        }

        // telemetry reporting
        utils.pushToSWA([step: STEP_NAME], config)
    }
}
