package security;

import at.favre.lib.crypto.bcrypt.BCrypt;

import javax.inject.Singleton;

/**
 * BCrypt implementation của PasswordHasher
 */
@Singleton
public class BCryptPasswordHasher implements PasswordHasher {
    private static final int COST = 11;

    @Override
    public String hash(String password) {
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray());
    }

    @Override
    public boolean verify(String password, String hash) {
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
        return result.verified;
    }
}
