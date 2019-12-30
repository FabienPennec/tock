/*
 * Copyright (C) 2017/2019 e-voyageurs technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.tock.bot.engine.message

import ai.tock.bot.connector.ConnectorMessage
import ai.tock.bot.connector.ConnectorType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SentenceTest {

    @Test
    fun `create Sentence with ConnectorMessage returns a GenericMessage with connector type and connector message`() {
        val c = mockk<ConnectorMessage>()
        val connectorType = ConnectorType("a")
        every { c.connectorType } returns connectorType
        val s = Sentence(null, mutableListOf(c))

        val g = s.messages.first()

        assertEquals(connectorType, g.connectorType)
        assertEquals(c, g.findConnectorMessage())
    }
}