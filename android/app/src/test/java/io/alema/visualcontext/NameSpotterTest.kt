package io.alema.visualcontext

import io.alema.facecore.NameSpotter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameSpotterTest {
    @Test fun introductions() {
        assertEquals("Ana", NameSpotter.find("essa é a Ana"))
        assertEquals("Pedro Reis", NameSpotter.find("…prazer, eu sou o Pedro Reis, da Torra Fina"))
        assertEquals("Bruno Galvão", NameSpotter.find("meu nome é bruno galvão"))
        assertEquals("Bia", NameSpotter.find("pode me chamar de Bia, tá?"))
        assertEquals("João", NameSpotter.find("o nome dele é João e ele trabalha comigo"))
        assertEquals("Carla", NameSpotter.find("prazer Carla"))
        assertEquals("Maria da Silva", NameSpotter.find("eu sou a Maria da Silva"))
        assertEquals("Anna", NameSpotter.find("this is Anna, my friend"))
    }

    @Test fun notNames() {
        assertNull(NameSpotter.find("eu sou designer"))
        assertNull(NameSpotter.find("eu sou de São Paulo"))
        assertNull(NameSpotter.find("tudo bem com você?"))
        assertNull(NameSpotter.find("esse é o fornecedor"))
    }

    @Test fun lastOneWins() {
        assertEquals("Rafael", NameSpotter.find("essa é a Ana. e esse é o Rafael"))
    }
}
