package security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit test cho BCryptPasswordHasher
 */
public class BCryptPasswordHasherTest {
    private final PasswordHasher passwordHasher = new BCryptPasswordHasher();

    @Test
    void hash_shouldGenerateValidHash() {
        String password = "TestPassword123!";
        String hash = passwordHasher.hash(password);
        
        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
        assertThat(hash).startsWith("$2a$");
    }

    @Test
    void verify_withCorrectPassword_shouldReturnTrue() {
        String password = "CorrectPassword456!";
        String hash = passwordHasher.hash(password);
        
        boolean result = passwordHasher.verify(password, hash);
        
        assertThat(result).isTrue();
    }

    @Test
    void verify_withWrongPassword_shouldReturnFalse() {
        String password = "CorrectPassword456!";
        String hash = passwordHasher.hash(password);
        
        boolean result = passwordHasher.verify("WrongPassword", hash);
        
        assertThat(result).isFalse();
    }

    @Test
    void hash_shouldGenerateDifferentHashesForSamePassword() {
        String password = "SamePassword789!";
        String hash1 = passwordHasher.hash(password);
        String hash2 = passwordHasher.hash(password);
        
        // Hashes khác nhau vì BCrypt dùng random salt
        assertThat(hash1).isNotEqualTo(hash2);
        
        // Nhưng cả 2 đều verify được
        assertThat(passwordHasher.verify(password, hash1)).isTrue();
        assertThat(passwordHasher.verify(password, hash2)).isTrue();
    }
}
