package mflix.api.daos;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadConcern;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Comment;
import mflix.api.models.Critic;
import mflix.api.models.User;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Component
public class CommentDao extends AbstractMFlixDao {

    public static String COMMENT_COLLECTION = "comments";
    public static String USERS_COLLECTION = "users";
    private final Logger log;
    private MongoCollection<Comment> commentCollection;
    private CodecRegistry pojoCodecRegistry;

    @Autowired
    private UserDao userDao;

    @Autowired
    public CommentDao(
            MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
        super(mongoClient, databaseName);
        log = LoggerFactory.getLogger(this.getClass());
        this.db = this.mongoClient.getDatabase(MFLIX_DATABASE);
        this.pojoCodecRegistry =
                fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        this.commentCollection =
                db.getCollection(COMMENT_COLLECTION, Comment.class).withCodecRegistry(pojoCodecRegistry);
    }

    /**
     * Returns a Comment object that matches the provided id string.
     *
     * @param id - comment identifier
     * @return Comment object corresponding to the identifier value
     */
    public Comment getComment(String id) {
        return commentCollection.find(new Document("_id", new ObjectId(id))).first();
    }

    /**
     * Adds a new Comment to the collection. The equivalent instruction in the mongo shell would be:
     *
     * <p>db.comments.insertOne({comment})
     *
     * <p>
     *
     * @param comment - Comment object.
     * @throw IncorrectDaoOperation if the insert fails, otherwise
     * returns the resulting Comment object.
     */
    public Comment addComment(Comment comment) throws IncorrectDaoOperation {

        // Update User reviews: implement the functionality that enables adding a new
        // comment.

        validateComment(comment);
        commentCollection.insertOne(comment);

        // TODO> Ticket - Handling Errors: Implement a try catch block to
        // handle a potential write exception when given a wrong commentId.
        return comment;
    }

    private void validateComment(Comment comment) {
        if (comment.getId() == null || comment.getId().isEmpty()) {
            throw new IncorrectDaoOperation("Empty comment ID");
        }
        if (comment.getText() == null || comment.getText().isEmpty()) {
            throw new IncorrectDaoOperation("Empty text");
        }
    }

    /**
     * Updates the comment text matching commentId and user email. This method would be equivalent to
     * running the following mongo shell command:
     *
     * <p>db.comments.update({_id: commentId}, {$set: { "text": text, date: ISODate() }})
     *
     * <p>
     *
     * @param commentId - comment id string value.
     * @param text      - comment text to be updated.
     * @param email     - user email.
     * @return true if successfully updates the comment text.
     */
    public boolean updateComment(String commentId, String text, String email) {

        // Update User reviews: implement the functionality that enables updating an
        // user own comments

        Bson findById = Filters.eq("_id", new ObjectId(commentId));

        Comment userComment = commentCollection.find(findById).first();
        if (email == null || userComment == null || !email.equals(userComment.getEmail())) {
           return false;
        }

        Bson fieldsToUpdate = Updates.combine(Updates.set("text", text), Updates.set("date", new Date()));
        UpdateOptions options = new UpdateOptions().upsert(true);
        UpdateResult updateResult = commentCollection.updateOne(findById, fieldsToUpdate, options);

        // TODO> Ticket - Handling Errors: Implement a try catch block to
        // handle a potential write exception when given a wrong commentId.
        return updateResult.wasAcknowledged();
    }

    /**
     * Deletes comment that matches user email and commentId.
     *
     * @param commentId - commentId string value.
     * @param email     - user email value.
     * @return true if successful deletes the comment.
     */
    public boolean deleteComment(String commentId, String email) {
        // Ticket Delete Comments - Implement the method that enables the deletion of a user
        // comment
        if (commentId == null) {
            throw new IncorrectDaoOperation("comment id is null");
        }
        Bson findCommentToDelete = Filters.and(
                Filters.eq("_id", new ObjectId(commentId)),
                       Filters.eq("email", email)
        );
        DeleteResult deleteResult = commentCollection.deleteOne(findCommentToDelete);
        // TIP: make sure to match only users that own the given commentId
        // TODO> Ticket Handling Errors - Implement a try catch block to
        // handle a potential write exception when given a wrong commentId.
        return deleteResult.getDeletedCount() > 0;
    }

    /**
     * Ticket: User Report - produce a list of users that comment the most in the website. Query the
     * `comments` collection and group the users by number of comments. The list is limited to up most
     * 20 commenter.
     *
     * @return List {@link Critic} objects.
     */
    public List<Critic> mostActiveCommenters() {
        // // Ticket: User Report - execute a command that returns the
        // // list of 20 users, group by number of comments. Don't forget,
        // // this report is expected to be produced with an high durability
        // // guarantee for the returned documents. Once a commenter is in the
        // // top 20 of users, they become a Critic, so mostActive is composed of
        // // Critic objects.
        List<Critic> mostActive = new ArrayList<>();
        List<Document> pipeline = new ArrayList<>();
        pipeline.addAll(Arrays.asList(new Document("$lookup",
                        new Document("from", "comments")
                                .append("let",
                                        new Document("email", "$email"))
                                .append("pipeline", Arrays.asList(new Document("$match",
                                                new Document("$expr",
                                                        new Document("$eq", Arrays.asList("$email", "$$email")))),
                                        new Document("$count", "count")))
                                .append("as", "count")),
                new Document("$sort",
                        new Document("count", -1L)),
                new Document("$limit", 20L),
                new Document("$project",
                        new Document("name", 0L)
                                .append("password", 0L)),
                new Document("$unwind",
                        new Document("path", "$count"))));
        MongoCollection<Document> usersCollection = db.getCollection(USERS_COLLECTION)
                .withCodecRegistry(pojoCodecRegistry)
                .withReadConcern(ReadConcern.MAJORITY);
        usersCollection.aggregate(pipeline).iterator().forEachRemaining(doc -> {
            mostActive.add(
                    new Critic(doc.get("email").toString(),
                    MovieDocumentMapper.parseInt(((Document)doc.get("count")).get("count")))
            );
        });
        return mostActive;
    }
}
