package org.example;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

/**
 * Main method where the core workflow of tasks is performed. This demonstrates
 * several key points including:
 * <ul>
 *     <li>Setting up a client</li>
 *     <li>List buckets</li>
 *     <li>Creating an S3 bucket</li>
 *     <li>Paginate operations for such things as listing buckets or listing bucket contents</li>
 *     <li>Putting an object in a bucket</li>
 *     <li>Generating a presigned URL</li>
 *     <li>Deleting a bucket</li>
 * </ul>
 */
public class Main {
    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(Main.class);
        /*
        var profile = "app-user";
        //var profile = "default";

        // Create Credentials provider using the default profile (in ~/.aws/config
        // ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();

        // Create Credentials provider using the specified profile
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create(profile);

        // Load Region from default config file for specified profile
        var regionProvider =  new AwsProfileRegionProvider(null, profile);
        var region = regionProvider.getRegion();
        logger.log(Level.DEBUG, "Region = " + region);
        logger.log(Level.INFO, "Starting using profile - " + profile);

        S3Client s3Client = S3Client.builder()
                // Region is optional and if specified may impact requests for list objects in S3 bucket not in that region
                // as well as what buckets are displayed (i.e. only buckets in region specified will be listed)
                .region(region)
                // Will list all buckets across all regions if true - new feature added in 2.20.111
                // https://aws.amazon.com/blogs/developer/introducing-s3-cross-region-support-in-the-aws-sdk-for-java-2-x/
                .crossRegionAccessEnabled(true)
                .credentialsProvider(credentialsProvider)
                .build();
         */

        /* Alternatively, use the default credentials, which can either be set via 'aws configure' or
           via inheriting the role's credentials from the ec2 instance or the lambda */
        Region region = Region.US_EAST_1;
        S3Client s3Client = S3Client.builder()
                .region(region)
                .crossRegionAccessEnabled(true)
                .build();
        // Used for the S3Query operation
        S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(region)
                .crossRegionAccessEnabled(true)
                .build();

        /* */

        logger.log(Level.DEBUG, "Listing bucket objects...");

        // List the buckets
        listBuckets(s3Client);

        // Test out creation of a new bucket and then delete it
        var newBucket = "mark-test-9702144567";
        createBucket(s3Client, newBucket);
        System.out.println("Bucket exists: " + bucketExists(s3Client, newBucket));

        // Put an object in the bucket
        String file = "notes.csv";
//        String file = "README.adoc";
        putObject(s3Client, newBucket, file);

        //listBucketObjects(s3Client, newBucket);
        pagingListBucketObjects(s3Client, newBucket);

        queryS3Object(s3AsyncClient, newBucket, file);

        String presignedUrl = getSignedUrl(s3Client, newBucket, file, 3600);
        System.out.println("\nPresigned URL: \n" + presignedUrl);

        deleteBucketWithObjects(s3Client, newBucket);
        s3Client.close();
        ;
    }

    /**
     * List the buckets and associated regions given the provided S3Client reference.
     *
     * @param client
     */
    static void listBuckets(S3Client client) {

        try {
            ListBucketsResponse buckets = client.listBuckets();
            System.out.println("Your S3 buckets are:");
            buckets.buckets().forEach(item -> {
                // For each bucket, get the bucket location (region) - note that if bucket location is in the
                // default region, an empty string is returned
                GetBucketLocationRequest.Builder bucketLocationRequest = GetBucketLocationRequest.builder().bucket(item.name());
                GetBucketLocationResponse bucketLocation = client.getBucketLocation(bucketLocationRequest.build());
                var region = bucketLocation.locationConstraintAsString() != "" ? bucketLocation.locationConstraintAsString() : "us-east-1";
                System.out.println("* " + item.name() + " - " + region);
            });
        } catch (AwsServiceException ex) {
            switch (ex.statusCode()) {
                case 404:
                    System.out.println("Bucket does not exist");
                case 400:
                    System.out.println("Bucket is not in the specified region");
                case 403:
                    System.out.println("You do not have permission to access the bucket");
            }
        } catch (SdkClientException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * List the bucket contents using paging
     * <p>
     * With the new capability enabled by the 'crossRegionAccessEnabled', this will be able to
     * list bucket contents in other regions than the configured region
     *
     * @param client
     * @param bucketName
     */
    static void pagingListBucketObjects(S3Client client, String bucketName) {
        ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                .bucket(bucketName).maxKeys(1).build();
        ListObjectsV2Iterable listRes = client.listObjectsV2Paginator(listReq);
        // Process response pages
        listRes.stream()
                .flatMap(r -> r.contents().stream())
                .forEach(content -> System.out.println(" Key: " + content.key()));
    }

    /**
     * List objects of a given bucket
     * <p>
     * With the new capability enabled by the 'crossRegionAccessEnabled', this will be able to
     * list bucket contents in other regions than the configured region
     *
     * @param client
     * @param bucketName
     */
    static void listBucketObjects(S3Client client, String bucketName) {
        System.out.println("The Contents of the '" + bucketName + "' :");

        try {
            ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder().bucket(bucketName).build();
            ListObjectsResponse listObjResponse = client.listObjects(listObjectsRequest);
            listObjResponse.contents().forEach(obj -> {
                System.out.println("Key: " + obj.key() + ", Owner: " + obj.owner() + ", Size: " + obj.size() / 1024 + " KBs");
            });
        } catch (AwsServiceException ex) {
            switch (ex.statusCode()) {
                case 301:  // Redirect (this produces an error as the correct URL is not provided)
                    System.out.println("Bucket is not in the specified region: " + ex.getMessage());
                    break;
                case 400:
                    System.out.println("Bucket is not in the specified region: " + ex.getMessage());
                    break;
                case 404:
                    System.out.println("Bucket does not exist");
                    break;
                default:
                    System.out.println("Error listing bucket contents: " + ex.statusCode());

            }
        } catch (SdkClientException e) {

            throw new RuntimeException(e);
        }

    }

    /**
     * Creates a bucket with the specified name.
     * Note: This will create the bucket in the region configured explicitly in the client - or the
     * default region if not specified.
     * This method will also check to see if the bucket already exists
     *
     * @param client     S3 Client initialized - potentially with the target region to create bucket in
     * @param bucketName Name of the bucket to create
     */
    static void createBucket(S3Client client, String bucketName) {
        try {
            if (!bucketExists(client, bucketName)) {
                S3Waiter waiter = client.waiter();
                CreateBucketRequest bucketRequest = CreateBucketRequest.builder().bucket(bucketName).build();
                client.createBucket(bucketRequest);
                HeadBucketRequest bucketRequestWait = HeadBucketRequest.builder().bucket(bucketName).build();

                // Wait for creation to finish
                WaiterResponse<HeadBucketResponse> waiterResponse = waiter.waitUntilBucketExists(bucketRequestWait);
                waiterResponse.matched().response().ifPresent(System.out::println);
                System.out.println("[" + bucketName + "] is ready");
            } else {
                System.out.println("Bucket already exists");
            }
        } catch (AwsServiceException exception) {
            System.err.println(exception.awsErrorDetails().errorCode());
            System.exit(1);
        }
    }

    /**
     * Puts a file from the local filesystem into the specified S3 bucket
     *
     * @param client     S3 Client already initialized
     * @param bucketName Bucket to put object into
     * @param filename   Source filename, which will also be the 'key' of the object
     */
    public static void putObject(S3Client client, String bucketName, String filename) {

        if (bucketExists(client, bucketName)) {
            Path path = Paths.get(filename);

            if (Files.exists(path)) {
                try {
                    PutObjectRequest objectRequest = PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(filename)
                            .build();

                    client.putObject(objectRequest, Paths.get(filename));
                } catch (AwsServiceException ex) {
                    System.err.println(ex.awsErrorDetails().errorCode());
                    System.exit(1);
                }
            } else {
                System.err.println("File doesn't exist: " + filename);
            }

        }
    }

    /**
     * To delete a bucket having objects in it, you must first delete the objects, then you
     * can delete the bucket. This code demonstrates that using the V2 SDK.
     *
     * @param client     S3 Client reference initialized
     * @param bucketName Bucket to delete
     */
    static void deleteBucketWithObjects(S3Client client, String bucketName) {
        try {
            if (bucketExists(client, bucketName)) {
                // To delete a bucket, all the objects in the bucket must be deleted first.
                ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .build();
                ListObjectsV2Response listObjectsV2Response;

                do {
                    listObjectsV2Response = client.listObjectsV2(listObjectsV2Request);
                    for (S3Object s3Object : listObjectsV2Response.contents()) {
                        DeleteObjectRequest request = DeleteObjectRequest.builder()
                                .bucket(bucketName)
                                .key(s3Object.key())
                                .build();
                        client.deleteObject(request);
                    }
                } while (listObjectsV2Response.isTruncated());
                DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
                client.deleteBucket(deleteBucketRequest);
            } else {
                System.out.println("Bucket doesn't exist");
            }

        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    /**
     * Uses the HeadBucket S3 call to obtain the status of the bucket.
     *
     * @param client
     * @param bucket
     * @return
     */
    static boolean bucketExists(S3Client client, String bucket) {
        HeadBucketRequest bucketRequest = HeadBucketRequest.builder().bucket(bucket).build();
        boolean result = false;
        try {
            client.headBucket(bucketRequest);
            result = true;
        } catch (NoSuchBucketException noSuchBucketException) {
            // Do nothing as the result is false
        } catch (AwsServiceException exception) {
            System.err.println(exception.awsErrorDetails().errorMessage());
        }
        return result;
    }

    /**
     * Perform an S3 select on the file, assuming the input type is CSV having the first
     * line contain the column names or labels.
     *
     * @param s3AsyncClient The Async version of the S3 client
     * @param bucket        Bucket containing the file to query
     * @param file          File to query on, which is assumed to be a CSV file
     */
    static void queryS3Object(S3AsyncClient s3AsyncClient, String bucket, String file) {
        // Perform S3 query
        System.out.printf("Querying file: %s in bucket %s\n", file, bucket);
        var query = "select * from s3object s where s.Notes like '%DynamoDB%'";
        // The input serialization is important generally to call out the type of the input file,
        // but more importantly because the above query references fields by name rather that by
        // number and that requires the setting below: .fileHeaderInfo(FileHeaderInfo.USE), which
        // essentially says 'use the first line of the file as a 'header' and use the labels as column names.
        InputSerialization inputSerialization = InputSerialization.builder()
                .csv(CSVInput.builder()
                        .fileHeaderInfo(FileHeaderInfo.USE)
                        .fieldDelimiter(",")
                        .build())
                .compressionType(CompressionType.NONE)
                .build();
        OutputSerialization outputSerialization = OutputSerialization.builder()
                .json(JSONOutput.builder().build())
                .build();
        // Build the request
        var request = SelectObjectContentRequest.builder()
                .bucket(bucket)
                .key(file)
                .expression(query)
                .expressionType(ExpressionType.SQL)
                .inputSerialization(inputSerialization)
                .outputSerialization(outputSerialization)
                .build();
        // Uses a 'handler' to receive the results as they are returned via the CompletableFuture
        var handler = new S3SelectHandler();

        try {
            // The 'get()' call causes the execution to wait until the request is complete.
            // This call is on the CompletableFuture returned by the selectObjectContent() call.
            s3AsyncClient.selectObjectContent(request, handler).get();

            // The 'getReceivedEvents()' call returns a list of the events that were received
            // by the handler, which can then be extracted and ultimately printed out.
            RecordsEvent response = (RecordsEvent) handler.getReceivedEvents().stream()
                    .filter(e -> e.sdkEventType() == SelectObjectContentEventStream.EventType.RECORDS)
                    .findFirst()
                    .orElse(null);
            System.out.println(response.payload().asUtf8String());
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Error caught: " + e.getMessage());
        }
    }

    /**
     * Generate a pre-signed URL for a given object in a specified bucket for a specified period of time.
     * <br>
     * Note: If the goal is to upload a file, then a different set of Request objects is required.
     * <pre>
     *     PutObjectRequest objectRequest = PutObjectRequest.builder()
     *                     .bucket(bucketName)
     *                     .key(keyName)
     *                     .build();
     *     PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
     *                     .signatureDuration(Duration.ofMinutes(10))  // The URL expires in 10 minutes.
     *                     .putObjectRequest(objectRequest)
     *                     .build();
     *     PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
     * </pre>
     *
     * @param client
     * @param bucketName
     * @param objectKey
     * @param duration   Duration for the credentials to be active (in seconds)
     * @return String representing the pre-signed URL
     */
    static public String getSignedUrl(S3Client client, String bucketName, String objectKey, long duration) {
        try (S3Presigner presigner = S3Presigner.create()) {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(duration))
                    .getObjectRequest(objectRequest)
                    .build();
            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toExternalForm();
        }
    }
}