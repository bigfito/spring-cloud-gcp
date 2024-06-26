/*
 * Copyright 2021-2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spanner.r2dbc.springdata.it;

import static com.google.cloud.spanner.r2dbc.SpannerConnectionFactoryProvider.INSTANCE;
import static com.google.cloud.spanner.r2dbc.SpannerConnectionFactoryProvider.PROJECT;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;

import com.google.cloud.ServiceOptions;
import com.google.cloud.spanner.r2dbc.springdata.it.entities.Address;
import com.google.cloud.spanner.r2dbc.springdata.it.entities.Person;
import com.google.cloud.spanner.r2dbc.v2.JsonWrapper;
import com.google.common.math.DoubleMath;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.test.StepVerifier;

/**
 * Integration tests for the Spring Data R2DBC dialect Json support.
 *
 * <p>By default, the test is configured to run tests in the `reactivetest` instance on the `testdb`
 * database. This can be configured by overriding the `spanner.instance` and `spanner.database`
 * system properties.
 */
@EnabledIfSystemProperty(named = "it.spanner", matches = "true")
class SpannerR2dbcDialectJsonIntegrationTest extends AbstractBaseSpannerR2dbcIntegrationTest {

  /**
   * Initializes the integration test environment for the Spanner R2DBC dialect.
   */
  @BeforeEach
  public void initializeTestEnvironment() {
    initializeTestEnvironment(SpannerR2dbcDialectJsonIntegrationTest.TestConfiguration.class,
        "CREATE TABLE PERSON ("
            + "  NAME STRING(256) NOT NULL,"
            + "  BIRTH_YEAR INT64 NOT NULL,"
            + "  EXTRAS JSON,"
            + "  ADDRESS JSON"
            + ") PRIMARY KEY (NAME)");
  }

  private void insertPerson(Person person, R2dbcEntityTemplate r2dbcEntityTemplate) {
    r2dbcEntityTemplate
        .insert(Person.class)
        .using(person)
        .then()
        .as(StepVerifier::create)
        .verifyComplete();
  }

  @Test
  void testReadWriteWithJsonFieldString() {
    Map<String, String> extras = new HashMap<>();
    extras.put("bio", "former U.S. president");
    extras.put("spouse", "Hillary Clinton");
    Person billClinton = new Person("Bill Clinton", 1946, extras, null);

    this.contextRunner.run(ctx -> {
      R2dbcEntityTemplate r2dbcEntityTemplate = ctx.getBean(R2dbcEntityTemplate.class);
      insertPerson(billClinton, r2dbcEntityTemplate);
      r2dbcEntityTemplate
          .select(Person.class)
          .first()
          .as(StepVerifier::create)
          .expectNextMatches(
              person ->
                  person.getName().equals("Bill Clinton")
                      && person.getBirthYear() == 1946
                      && person.getExtras().getOrDefault("spouse", "none").equals("Hillary Clinton")
                      && person
                      .getExtras()
                      .getOrDefault("bio", "none")
                      .equals("former U.S. president"))
          .verifyComplete();
    });
  }

  @Test
  void testReadWriteWithJsonFieldBoolean() {
    Map<String, Boolean> extras = new HashMap<>();
    extras.put("male", true);
    extras.put("US citizen", true);
    Person billClinton = new Person("Bill Clinton", 1946, extras, null);

    this.contextRunner.run(ctx -> {
      R2dbcEntityTemplate r2dbcEntityTemplate = ctx.getBean(R2dbcEntityTemplate.class);
      insertPerson(billClinton, r2dbcEntityTemplate);
      r2dbcEntityTemplate
          .select(Person.class)
          .first()
          .as(StepVerifier::create)
          .expectNextMatches(
              person ->
                  person.getName().equals("Bill Clinton")
                      && person.getBirthYear() == 1946
                      && person.getExtras().getOrDefault("male", false).equals(true)
                      && person.getExtras().getOrDefault("US citizen", false).equals(true))
          .verifyComplete();
    });
  }

  @Test
  void testReadWriteWithJsonFieldDouble() {
    Map<String, Double> extras = new HashMap<>();
    extras.put("weight", 144.5);
    extras.put("height", 5.916);
    Person<Double> billClinton = new Person("John Doe", 1946, extras, null);

    this.contextRunner.run(ctx -> {
      R2dbcEntityTemplate r2dbcEntityTemplate = ctx.getBean(R2dbcEntityTemplate.class);
      insertPerson(billClinton, r2dbcEntityTemplate);
      r2dbcEntityTemplate
          .select(Person.class)
          .first()
          .as(StepVerifier::create)
          .expectNextMatches(
              person ->
                  person.getName().equals("John Doe")
                      && person.getBirthYear() == 1946
                      && DoubleMath.fuzzyEquals(
                      (double) person.getExtras().get("weight"), 144.5, 1e-5)
                      && DoubleMath.fuzzyEquals(
                      (double) person.getExtras().get("height"), 5.916, 1e-5))
          .verifyComplete();
    });
  }

  @Test
  void testReadWriteWithJsonFieldCustomClass() {
    Address address = new Address("home address", "work address", 10012, 10011);
    Person billClinton = new Person("Bill Clinton", 1946, null, address);
    this.contextRunner.run(ctx -> {
      R2dbcEntityTemplate r2dbcEntityTemplate = ctx.getBean(R2dbcEntityTemplate.class);
      insertPerson(billClinton, r2dbcEntityTemplate);
      r2dbcEntityTemplate
          .select(Person.class)
          .first()
          .as(StepVerifier::create)
          .expectNextMatches(
              person ->
                  person.getName().equals("Bill Clinton")
                      && person.getBirthYear() == 1946
                      && person.getAddress().equals(address))
          .verifyComplete();
    });
  }

  /** Register custom converters between Map and JsonWrapper. */
  @Configuration
  static class TestConfiguration extends AbstractR2dbcConfiguration {
    @Value("${testDatabase}")
    private String testDatabase;

    @Autowired ApplicationContext applicationContext;

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
      return ConnectionFactories.get(
          ConnectionFactoryOptions.builder()
              .option(Option.valueOf("project"), ServiceOptions.getDefaultProjectId())
              .option(PROJECT, PROJECT_NAME)
              .option(DRIVER, DRIVER_NAME)
              .option(INSTANCE, TEST_INSTANCE)
              .option(DATABASE, testDatabase)
              .build());
    }

    @Bean
    public Gson gson() {
      return new Gson();
    }

    @Bean
    @Override
    public R2dbcCustomConversions r2dbcCustomConversions() {
      List<Converter<?, ?>> converters = new ArrayList<>();
      converters.add(this.applicationContext.getBean(JsonToReviewsConverter.class));
      converters.add(this.applicationContext.getBean(ReviewsToJsonConverter.class));
      return new R2dbcCustomConversions(getStoreConversions(), converters);
    }

    @Component
    @ReadingConverter
    public class JsonToReviewsConverter implements Converter<JsonWrapper, Address> {

      private final Gson gson;

      @Autowired
      public JsonToReviewsConverter(Gson gson) {
        this.gson = gson;
      }

      @Override
      public Address convert(JsonWrapper json) {
        try {
          return this.gson.fromJson(json.toString(), Address.class);
        } catch (JsonParseException e) {
          return new Address();
        }
      }
    }

    @Component
    @WritingConverter
    public class ReviewsToJsonConverter implements Converter<Address, JsonWrapper> {

      private final Gson gson;

      @Autowired
      public ReviewsToJsonConverter(Gson gson) {
        this.gson = gson;
      }

      @Override
      public JsonWrapper convert(Address source) {
        try {
          return JsonWrapper.of(this.gson.toJson(source));
        } catch (JsonParseException e) {
          return JsonWrapper.of("");
        }
      }
    }
  }
}
