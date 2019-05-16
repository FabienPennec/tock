package fr.vsct.tock.nlp.dialogflow

import com.google.cloud.dialogflow.v2.*
import mu.KotlinLogging


object DialogflowService {

    private val logger = KotlinLogging.logger {}

    /**
     * Returns the result of detect intent with text as input.
     *
     * Using the same `session_id` between requests allows continuation of the conversation.
     *
     * @param projectId    Project/Agent Id.
     * @param text         The text intent to be detected based on what a user says.
     * @param sessionId    Identifier of the DetectIntent session.
     * @param languageCode Language code of the query.
     * @return The QueryResult for the input text.
     */
    fun detectIntentText(
        projectId: String,
        text: String,
        sessionId: String,
        languageCode: String) : QueryResult? {

        SessionsClient.create().use {
            // Set the session name using the sessionId (UUID) and projectID (my-project-id)
            val session = SessionName.of(projectId, sessionId)
            logger.debug("Session Path: $session")

            // Set the text (hello) and language code (en-US) for the query
            TextInput.newBuilder().setText(text).setLanguageCode(languageCode)
                .apply {
                    QueryInput.newBuilder().setText(this).build().apply {
                        it.detectIntent(session, this).apply {
                            return this.queryResult.also {
                                logger.debug("Query Text: '${it.queryText}'")
                                logger.debug("Detected Intent: ${it.intent.displayName} (confidence: ${it.intentDetectionConfidence})")
                            }
                        }
                    }
                }
        }
        return null
    }

    /**
     * Retrieves the [Agent] of the given [projectId].
     */
    fun getAgent(projectId: String): Agent? {
        AgentsClient.create().use {
            val parent: ProjectName = ProjectName.of(projectId)
            return it.getAgent(parent)
        }
    }

}

