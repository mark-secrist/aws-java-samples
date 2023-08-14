package org.example;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;

public class Main {
    public static void main(String[] args) {
        // Create Credentials provider using the default provider (in ~/.aws/config
        //ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
        //S3Client s3Client = S3Client.builder().region(Region.US_WEST_2).credentialsProvider(credentialsProvider).build();
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
        Region region = Region.US_EAST_1;
        S3Client s3Client = S3Client.builder()
                // Region is optional and if specified may impact requests for list objects in S3 bucket not in
                // specified region
                //.region(region)
                .credentialsProvider(credentialsProvider)
                .build();


        // List the buckets
        listBuckets(s3Client);

        // List objects in mark-bucket-2
        var bucketName = "marks-web";
        //var bucketName = "cf-templates-142hd9b6p9a8s-us-west-2";
        listBucketObjects(s3Client, bucketName);

        var newBucket = "mark-test-9702144567";
        createBucket(s3Client, newBucket);

        s3Client.close();;
    }

    /**
     * List the buckets and associated regions given the provided S3Client reference.
     *
     * Note that if a bucket is in the default region (us-east-1), then the region returned is ""
     * or NULL
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
                    System.out.println("1-Bucket is not in the specified region");
                    break;
                case 400:
                    System.out.println("2-Bucket is not in the specified region");
                    break;
                case 404:
                    System.out.println("3-Bucket does not exist");
                    break;
                default:
                    System.out.println("4-Error listing bucket contents: " + ex.statusCode());

            }
        } catch (SdkClientException e) {

            throw new RuntimeException(e);
        }

    }

    static void createBucket(S3Client client, String bucketName) {
        try {
            if (! bucketExists(client, bucketName)) {
                S3Waiter waiter = client.waiter();
                CreateBucketRequest bucketRequest = CreateBucketRequest.builder().bucket(bucketName).build();
                client.createBucket(bucketRequest);
                HeadBucketRequest bucketRequeestWait = HeadBucketRequest.builder().bucket(bucketName).build();

                // Wait for creation to finish
                WaiterResponse<HeadBucketResponse> waiterResponse = waiter.waitUntilBucketExists(bucketRequeestWait);
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