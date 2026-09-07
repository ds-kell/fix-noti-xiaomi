package io.github.hypernotifyfix
import io.github.hypernotifyfix.data.Redactor
import org.junit.Assert.*
import org.junit.Test
class RedactorTest { @Test fun reportRedactsSecretsAndPrivatePaths() { val value = Redactor.redact("token=abc123 /data/user/0/com.foo/files/a"); assertFalse(value.contains("abc123")); assertFalse(value.contains("com.foo")) } }
