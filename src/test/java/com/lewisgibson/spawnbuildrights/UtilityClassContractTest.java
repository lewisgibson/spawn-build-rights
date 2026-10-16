package com.lewisgibson.spawnbuildrights;

import static org.assertj.core.api.Assertions.assertThat;

import com.lewisgibson.spawnbuildrights.core.conflict.SpawnProtectionConflict;
import com.lewisgibson.spawnbuildrights.core.message.Placeholders;
import com.lewisgibson.spawnbuildrights.core.time.PlaytimeDuration;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Enforces the utility-class contract across the stateless helpers: each must be final and
 * impossible to instantiate. Invoking the private constructor here also documents that intent (and
 * keeps the helpers honestly at 100% coverage rather than carrying an untested constructor).
 */
@DisplayName("Utility classes")
class UtilityClassContractTest {

    @ParameterizedTest
    @ValueSource(
            classes = {Placeholders.class, PlaytimeDuration.class, SpawnProtectionConflict.class})
    @DisplayName("are final with a single, private, no-arg constructor")
    void areNonInstantiableUtilities(Class<?> type) throws Exception {
        assertThat(Modifier.isFinal(type.getModifiers()))
                .as("%s should be final", type.getName())
                .isTrue();

        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertThat(constructors)
                .as("%s should declare exactly one constructor", type.getName())
                .hasSize(1);

        Constructor<?> constructor = constructors[0];
        assertThat(Modifier.isPrivate(constructor.getModifiers()))
                .as("%s constructor should be private", type.getName())
                .isTrue();
        assertThat(constructor.getParameterCount()).isZero();

        constructor.setAccessible(true);
        constructor.newInstance();
    }
}
