package mflix.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Configuration
@Service
public class MongoDBConfiguration {

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public MongoClient mongoClient(@Value("${spring.mongodb.uri}") String connectionString) {

        ConnectionString connString = new ConnectionString(connectionString);

        // Ticket: Handling Timeouts - configure the expected
        // WriteConcern `wtimeout` and `connectTimeoutMS` values

// ------ Possible solution, but made in URI

//        final MongoClientSettings settings = MongoClientSettings
//                .builder()
//                .applicationName("mflix")
//                .applyConnectionString(connString)
//                .writeConcern(new WriteConcern("majority").withWTimeout(2500, TimeUnit.MILLISECONDS))
//                .applyToClusterSettings(builder -> builder.applyConnectionString(connString)
//                        .serverSelectionTimeout(5000, TimeUnit.MILLISECONDS)
//                        .build())
//                .applyToConnectionPoolSettings(builder -> builder.applyConnectionString(connString)
//                        .maxSize(50)
//                        .build())
//                .applyToSocketSettings(builder -> builder.applyConnectionString(connString)
//                        .connectTimeout(2000, TimeUnit.MILLISECONDS)
//                        .build())
//                .build();
//
//        MongoClient mongoClient = MongoClients.create(settings);
//
//        return mongoClient;

        MongoClient mongoClient = MongoClients.create(connectionString);

        return mongoClient;
    }
}
