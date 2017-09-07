/*
 * Copyright (C) 2017 VSCT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.vsct.tock.nlp.core.service

import com.github.salomonbrys.kodein.instance
import fr.vsct.tock.nlp.core.Application
import fr.vsct.tock.nlp.core.BuildContext
import fr.vsct.tock.nlp.core.EntityRecognition
import fr.vsct.tock.nlp.core.Intent
import fr.vsct.tock.nlp.core.ModelCore
import fr.vsct.tock.nlp.core.quality.EntityMatchError
import fr.vsct.tock.nlp.core.quality.IntentMatchError
import fr.vsct.tock.nlp.core.quality.TestContext
import fr.vsct.tock.nlp.core.quality.TestModelReport
import fr.vsct.tock.nlp.core.sample.SampleEntity
import fr.vsct.tock.nlp.core.sample.SampleExpression
import fr.vsct.tock.nlp.model.EntityBuildContextForIntent
import fr.vsct.tock.nlp.model.IntentContext
import fr.vsct.tock.nlp.model.NlpClassifier
import fr.vsct.tock.shared.error
import fr.vsct.tock.shared.injector
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.util.Collections

/**
 *
 */
object ModelCoreService : ModelCore {

    private val logger = KotlinLogging.logger {}

    private val nlpClassifier: NlpClassifier by injector.instance()

    override fun updateIntentModel(context: BuildContext, expressions: List<SampleExpression>) {
        val nlpContext = IntentContext(context)
        if (!context.onlyIfNotExists || !nlpClassifier.isIntentModelExist(nlpContext)) {
            nlpClassifier.buildAndSaveIntentModel(nlpContext, expressions)
        }
    }

    override fun updateEntityModelForIntent(context: BuildContext, intent: Intent, expressions: List<SampleExpression>) {
        val nlpContext = EntityBuildContextForIntent(context, intent)
        if (!context.onlyIfNotExists
                || !nlpClassifier.isEntityModelExist(nlpContext)) {
            nlpClassifier.buildAndSaveEntityModel(nlpContext, expressions)
        }
    }

    override fun deleteOrphans(applicationsAndIntents: Map<Application, Set<Intent>>) {
        nlpClassifier.deleteOrphans(applicationsAndIntents)
    }

    override fun testModel(context: TestContext, expressions: List<SampleExpression>): TestModelReport {
        if (expressions.size < 100) {
            error("at least 100 expressions needed")
        }
        val shuffle = expressions.toMutableList()
        Collections.shuffle(shuffle)
        val limit = (expressions.size * context.threshold).toInt()
        val modelExpressions = shuffle.subList(0, limit)
        val testedExpressions = shuffle.subList(limit, shuffle.size)

        val startDate = Instant.now()

        val intentContext = IntentContext(context)
        val intentModel = nlpClassifier.buildIntentModel(intentContext, modelExpressions)
        val entityModels = modelExpressions
                .groupBy { it.intent }
                .mapNotNull { (intent, expressions)
                    ->
                    try {
                        intent to nlpClassifier.buildEntityModel(
                                EntityBuildContextForIntent(context, intent),
                                expressions)
                    } catch (e: Exception) {
                        logger.error { "entity model build fail for $intent " }
                        logger.error(e)
                        null
                    }
                }
                .toMap()

        val buildDuration = Duration.between(startDate, Instant.now())

        val intentErrors = mutableListOf<IntentMatchError>()
        val entityErrors = mutableListOf<EntityMatchError>()

        testedExpressions.forEach {
            val parseResult = NlpCoreService.parse(
                    context,
                    it.text,
                    intentModel,
                    entityModels
            )
            if (parseResult.intent != it.intent.name) {
                intentErrors.add(IntentMatchError(it, parseResult.intent, parseResult.intentProbability))
            } else if (hasNotSameEntities(it.entities, parseResult.entities)) {
                entityErrors.add(EntityMatchError(it, parseResult.entities))
            }
        }

        val testDuration = Duration.between(startDate.plus(buildDuration), Instant.now())

        return TestModelReport(
                expressions,
                testedExpressions,
                intentErrors,
                entityErrors,
                buildDuration,
                testDuration,
                startDate
        )
    }

    private fun hasNotSameEntities(
            expectedEntities: List<SampleEntity>,
            entities: List<EntityRecognition>): Boolean {
        return expectedEntities.any { e -> entities.none { it.role == e.definition.role && it.entityType == e.definition.entityType && it.isSameRange(e) } }
                || entities.any { expectedEntities.none { e -> it.role == e.definition.role && it.entityType == e.definition.entityType && it.isSameRange(e) } }
    }
}