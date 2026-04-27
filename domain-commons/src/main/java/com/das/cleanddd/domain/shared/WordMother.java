package com.das.cleanddd.domain.shared;

public final class WordMother {
    public static String random() {
        return MotherCreator.random().lorem().word();
    }
}
