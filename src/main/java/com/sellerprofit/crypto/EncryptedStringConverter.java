package com.sellerprofit.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * 엔티티에는 평문 String, DB에는 암호화된 byte[](BYTEA) 로 저장.
 * @Convert(converter = EncryptedStringConverter.class) 를 필드에 붙여 사용.
 *
 * Spring Boot 3 / Hibernate 6 은 컨버터를 Spring 빈으로 관리하므로
 * 아래처럼 생성자 주입이 그대로 동작한다(별도 설정 불필요).
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

    private final AesGcmEncryptor encryptor;

    public EncryptedStringConverter(AesGcmEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        return dbData == null ? null : encryptor.decrypt(dbData);
    }
}
