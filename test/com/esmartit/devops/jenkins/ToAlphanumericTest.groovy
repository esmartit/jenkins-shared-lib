package com.esmartit.devops.jenkins

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class ToAlphanumericTest extends BasePipelineTest {

    def toAlphanumeric

    @Before
    void setUp() {
        super.setUp()
        // load toAlphanumeric
        toAlphanumeric = loadScript("vars/toAlphanumeric.groovy")
    }

    @Test
    void testCall() {
        // call toAlphanumeric and check result
        def result = toAlphanumeric(text: "a_B-c.1")
        assertThat(result as String).isEqualTo("abc1")
    }

}
