package mflix.api.daos;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Session;
import mflix.api.models.User;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
public class UserDao extends AbstractMFlixDao {

    private final MongoCollection<User> usersCollection;
    //User Management - do the necessary changes so that the sessions collection
    //returns a Session object
    private final MongoCollection<Session> sessionsCollection;

    private final Logger log;

    @Autowired
    public UserDao(
            MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
        super(mongoClient, databaseName);
        CodecRegistry pojoCodecRegistry =
                fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        usersCollection = db.getCollection("users", User.class).withCodecRegistry(pojoCodecRegistry);
        log = LoggerFactory.getLogger(this.getClass());
        // Ticket: User Management - implement the necessary changes so that the sessions
        // collection returns a Session objects instead of Document objects.
        sessionsCollection = db.getCollection("sessions", Session.class).withCodecRegistry(pojoCodecRegistry);
    }

    /**
     * Inserts the `user` object in the `users` collection.
     *
     * @param user - User object to be added
     * @return True if successful, throw IncorrectDaoOperation otherwise
     */
    public boolean addUser(User user) {
        //Ticket: Durable Writes -  you might want to use a more durable write concern here!

        // Ticket: Handling Errors - make sure to only add new users
        // and not users that already exist.
        if(usersCollection.find(Filters.eq("email", user.getEmail())).first() != null) {
            throw new IncorrectDaoOperation("Such user already exists");
        }
        try {
            usersCollection.withWriteConcern(WriteConcern.MAJORITY).insertOne(user);
        } catch (MongoWriteException e) {
            System.out.println("Error occured: addUser() " + "Category: " + e.getError().getCategory());
            return false;
        }
        return true;
    }

    /**
     * Creates session using userId and jwt token.
     *
     * @param userId - user string identifier
     * @param jwt    - jwt string token
     * @return true if successful
     */
    public boolean createUserSession(String userId, String jwt) {
        // Ticket: Handling Errors - implement a safeguard against
        // creating a session with the same jwt token.
        if (sessionsCollection.find(Filters.eq("jwt", jwt)).first() != null) {
           return false;
        }

        // Ticket: User Management - implement the method that allows session information to be
        // stored in it's designated collection.
        if (Optional.ofNullable(sessionsCollection.find( Filters.eq("user_id", userId) ).first()).isPresent()) {
            sessionsCollection.updateOne(Filters.eq("user_id", userId), Updates.set("jwt", jwt));
        } else {
            sessionsCollection.insertOne(new Session(userId, jwt));
        }
        return true;
    }

    /**
     * Returns the User object matching the an email string value.
     *
     * @param email - email string to be matched.
     * @return User object or null.
     */
    public User getUser(String email) {
        // Ticket: User Management - implement the query that returns the first User object.
        Bson findUserByEmailQuery = Filters.eq("email", email);
        return usersCollection.find(findUserByEmailQuery).first();
    }

    /**
     * Given the userId, returns a Session object.
     *
     * @param userId - user string identifier.
     * @return Session object or null.
     */
    public Session getUserSession(String userId) {
        // Ticket: User Management - implement the method that returns Sessions for a given
        // userId
        Bson findUserByUserIdQuery = Filters.eq("user_id", userId);
        return sessionsCollection.find(findUserByUserIdQuery).first();
    }

    public boolean deleteUserSessions(String userId) {
        // User Management - implement the delete user sessions method
        Bson userToDeleteQuery = Filters.eq("user_id", userId);
        DeleteResult deleteSessionResult = sessionsCollection.deleteOne(userToDeleteQuery);
        return deleteSessionResult.getDeletedCount() > 0;
    }

    /**
     * Removes the user document that match the provided email.
     *
     * @param email - of the user to be deleted.
     * @return true if user successfully removed
     */
    public boolean deleteUser(String email) {
        // remove user sessions
        // User Management - implement the delete user method
        Bson userToDeleteQuery = Filters.eq("email", email);
        // Ticket: Handling Errors - make this method more robust by
        // handling potential exceptions.
        DeleteResult deleteUserResult;
        try {
            deleteUserResult = usersCollection.deleteOne(userToDeleteQuery);
        } catch (MongoException e) {
            System.out.println("Error occured: deleteUser(): " + e.getMessage());
            return false;
        }
        Bson sessionToDeleteQuery = Filters.eq("user_id", email);
        sessionsCollection.deleteMany(sessionToDeleteQuery);

        return deleteUserResult.getDeletedCount() > 0;
    }

    /**
     * Updates the preferences of an user identified by `email` parameter.
     *
     * @param email           - user to be updated email
     * @param userPreferences - set of preferences that should be stored and replace the existing
     *                        ones. Cannot be set to null value
     * @return User object that just been updated.
     */
    public boolean updateUserPreferences(String email, Map<String, ?> userPreferences) {
        // Ticket: User Preferences - implement the method that allows for user preferences to
        // be updated.

        if (userPreferences == null || userPreferences.isEmpty()) {
            throw new IncorrectDaoOperation("User preferences can not be null of empty");
        }

        Bson findUserByEmailQuery = Filters.eq("email", email);
        Document prefsDoc = new Document();

        for (String prefKey : userPreferences.keySet()) {
            prefsDoc.append(prefKey, String.valueOf(userPreferences.get(prefKey)));
        }

        // Ticket: Handling Errors - make this method more robust by
        // handling potential exceptions when updating an entry.
        try {
            usersCollection.updateOne(
                    findUserByEmailQuery,
                    Updates.set("preferences", prefsDoc),
                    new UpdateOptions().upsert(true)
            );
        } catch (MongoException e) {
            System.out.println("Error occured: updateUserPreferences(): " + e.getMessage());
            return false;
        }

        return true;
    }
}
