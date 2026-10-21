package com.gathin.files;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FilesSmokeTest {
    @Test
    public void packageConstant() {
        assertEquals("com.gathin.files", FilesInfo.PACKAGE);
        assertEquals("FILES", FilesInfo.TITLE);
    }
}
