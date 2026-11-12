package io.alema.zap

import com.anezium.rokidbus.shared.skills.SkillCatalogParseResult
import com.anezium.rokidbus.shared.skills.SkillCatalogParser
import io.alema.zap.core.ZapSkills
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class SkillsCatalogTest {
    /** The hub refuses an invalid catalog silently (Plugin access shows why); catch it here instead. */
    @Test
    fun `the skills catalog passes the Nexus SDK's own parser and matches what Zap answers`() {
        val json = File("src/main/res/raw/nexus_skills.json").readText()
        when (val result = SkillCatalogParser.parse(json)) {
            is SkillCatalogParseResult.Invalid -> fail("catalog invalid: ${result.reason}")
            is SkillCatalogParseResult.Valid -> assertEquals(
                setOf(ZapSkills.LIST_CHATS, ZapSkills.READ_CHAT, ZapSkills.RECENT, ZapSkills.SEND),
                result.catalog.operations.map { it.id }.toSet(),
            )
        }
    }
}
