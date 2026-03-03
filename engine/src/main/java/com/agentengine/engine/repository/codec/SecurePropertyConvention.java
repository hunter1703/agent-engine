package com.agentengine.engine.repository.codec;

import com.agentengine.engine.api.beans.Secure;
import com.agentengine.engine.utils.EncryptionService;
import jakarta.enterprise.inject.Instance;
import org.bson.codecs.pojo.ClassModelBuilder;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.PropertyModelBuilder;

import java.lang.reflect.Method;

public class SecurePropertyConvention implements Convention {

  private final Instance<EncryptionService> encryptionService;

  public SecurePropertyConvention(Instance<EncryptionService> encryptionService) {
    this.encryptionService = encryptionService;
  }

  @Override
  public void apply(final ClassModelBuilder<?> classModelBuilder) {
    for (final PropertyModelBuilder<?> propertyModelBuilder :
        classModelBuilder.getPropertyModelBuilders()) {
        
      boolean isSecure = false;
      
      // 1. Check property model annotations (fields)
      if (propertyModelBuilder.getReadAnnotations() != null) {
        isSecure = propertyModelBuilder.getReadAnnotations().stream()
                .anyMatch(a -> a.annotationType().equals(Secure.class));
      }
      if (!isSecure && propertyModelBuilder.getWriteAnnotations() != null) {
        isSecure = propertyModelBuilder.getWriteAnnotations().stream()
                .anyMatch(a -> a.annotationType().equals(Secure.class));
      }
      
      // 2. Explicitly check the Class methods for getters/setters (MongoDB PojoCodec bug workaround)
      if (!isSecure) {
         try {
             Class<?> clazz = classModelBuilder.getType();
             String propName = propertyModelBuilder.getName();
             String capitalizedName = propName.substring(0, 1).toUpperCase() + propName.substring(1);
             
             // Check getter
             try {
                Method getter = clazz.getMethod("get" + capitalizedName);
                if (getter.isAnnotationPresent(Secure.class)) {
                   isSecure = true;
                }
             } catch (NoSuchMethodException ignored) {}
             
             // Check setter
             if (!isSecure) {
               try {
                  Method setter = clazz.getMethod("set" + capitalizedName, String.class);
                  if (setter.isAnnotationPresent(Secure.class)) {
                     isSecure = true;
                  }
               } catch (NoSuchMethodException ignored) {}
             }
         } catch (Exception ignored) {
            // Ignore reflection errors for edge cases
         }
      }
      
      if (isSecure) {
        @SuppressWarnings("unchecked")
        final PropertyModelBuilder<String> stringBuilder = 
            (PropertyModelBuilder<String>) propertyModelBuilder;
        stringBuilder.codec(new SecureStringCodec(encryptionService));
      }
    }
  }
}
