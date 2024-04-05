package org.example;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.providers.AwsProfileRegionProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class Main {
    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(Main.class);
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

        logger.log(Level.DEBUG, "Listing bucket objects...");

        // List the buckets
        listBuckets(s3Client);

        // Test out creation of a new bucket and then delete it
        var newBucket = "mark-test-9702144567";
        createBucket(s3Client, newBucket);
        System.out.println("Bucket exists: " + bucketExists(s3Client, newBucket));

        // Put an object in the bucket
        String file = "README.adoc";
        putObject(s3Client, newBucket, file);

        //listBucketObjects(s3Client, newBucket);
        pagingListBucketObjects(s3Client, newBucket);

        deleteBucketWithObjects(s3Client, newBucket);
        s3Client.close();;
    }

    /**
     * List the buckets and associated regions given the provided S3Client reference.
     *
     * With the new capability enabled by the 'crossRegionAccessEnabled', this will be able to
     * list bucket contents in other regions than the configured region
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
     *
     * Note: The bucket provided must be in the region specified on the S3Client if region is specified
     * at all
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
                System.out.println("Key: " + obj.key() + ", Owner: " + obj.owner() + ", Size: " + obj.size()/1024 + " KBs");
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
     * @param client S3 Client initialized - potentially with the target region to create bucket in
     * @param bucketName Name of the bucket to create
     */
    static void createBucket(S3Client client, String bucketName) {
        try {
            if (! bucketExists(client, bucketName)) {
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
     * @param client S3 Client already initialized
     * @param bucketName Bucket to put object into
     * @param filename Source filename, which will also be the 'key' of the object
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
     * @param client S3 Client reference initialized
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

}