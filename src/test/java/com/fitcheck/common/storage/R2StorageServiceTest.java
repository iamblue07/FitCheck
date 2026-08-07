package com.fitcheck.common.storage;

import com.fitcheck.common.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    private static final String BUCKET = "fitcheck-test-bucket";
    private static final String KEY = "body-photos/some-user-id/front.jpg";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private R2StorageService storageService;

    @BeforeEach
    void setUp() {
        R2Properties properties = new R2Properties(
                "https://example.r2.cloudflarestorage.com", "access-key", "secret-key", BUCKET);
        storageService = new R2StorageService(s3Client, s3Presigner, properties);
    }

    private URL testUrl() throws Exception {
        return URI.create("https://" + BUCKET + ".r2.cloudflarestorage.com/" + KEY + "?X-Amz-Signature=abc").toURL();
    }

    // --- generateUploadUrl / generateDownloadUrl — no retry logic wraps these ---

    @Test
    void generateUploadUrl_callsPresignerWithCorrectBucketKeyAndContentType() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(testUrl());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        storageService.generateUploadUrl(KEY, "image/jpeg", Duration.ofMinutes(15));

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());

        PutObjectRequest captured = captor.getValue().putObjectRequest();
        assertThat(captured.bucket()).isEqualTo(BUCKET);
        assertThat(captured.key()).isEqualTo(KEY);
        assertThat(captured.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void generateUploadUrl_respectsConfiguredTtl() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(testUrl());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        Duration ttl = Duration.ofMinutes(7);
        storageService.generateUploadUrl(KEY, "image/jpeg", ttl);

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(ttl);
    }

    @Test
    void generateDownloadUrl_callsPresignerWithCorrectBucketAndKey() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(testUrl());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        storageService.generateDownloadUrl(KEY, Duration.ofMinutes(15));

        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());

        GetObjectRequest captured = captor.getValue().getObjectRequest();
        assertThat(captured.bucket()).isEqualTo(BUCKET);
        assertThat(captured.key()).isEqualTo(KEY);
    }

    @Test
    void generateUploadUrl_sdkException_wrappedAsExternalServiceException() {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenThrow(SdkException.builder().message("presigner unreachable").build());

        assertThatThrownBy(() -> storageService.generateUploadUrl(KEY, "image/jpeg", Duration.ofMinutes(15)))
                .isInstanceOf(ExternalServiceException.class);

        verify(s3Presigner, times(1)).presignPutObject(any(PutObjectPresignRequest.class));
    }

    // --- store — wrapped in retry logic ---

    @Test
    void store_uploadsBytesViaS3Client() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storageService.store(KEY, "fake image bytes".getBytes(), "image/jpeg");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(KEY);
        assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void store_transientFailure_retriesThenSucceeds() {
        S3Exception serverError = (S3Exception) S3Exception.builder().statusCode(503).message("service unavailable").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(serverError)
                .thenReturn(PutObjectResponse.builder().build());

        storageService.store(KEY, "bytes".getBytes(), "image/jpeg");

        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void store_nonRetryableFailure_failsImmediatelyWithoutRetrying() {
        S3Exception clientError = (S3Exception) S3Exception.builder().statusCode(403).message("access denied").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(clientError);

        assertThatThrownBy(() -> storageService.store(KEY, "bytes".getBytes(), "image/jpeg"))
                .isInstanceOf(ExternalServiceException.class);

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void store_allAttemptsFail_exhaustsRetriesAndThrowsExternalServiceException() {
        S3Exception serverError = (S3Exception) S3Exception.builder().statusCode(500).message("internal error").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(serverError);

        assertThatThrownBy(() -> storageService.store(KEY, "bytes".getBytes(), "image/jpeg"))
                .isInstanceOf(ExternalServiceException.class);

        verify(s3Client, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // --- exists ---

    @Test
    void exists_returnsTrueWhenObjectFound() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        assertThat(storageService.exists(KEY)).isTrue();
    }

    @Test
    void exists_returnsFalseWhenNoSuchKeyException() {
        S3Exception notFound = (S3Exception) S3Exception.builder().statusCode(404).message("not found").build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(notFound);

        assertThat(storageService.exists(KEY)).isFalse();
        verify(s3Client, times(1)).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void exists_transientFailure_retriesThenSucceeds() {
        S3Exception serverError = (S3Exception) S3Exception.builder().statusCode(500).message("internal error").build();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(serverError)
                .thenReturn(HeadObjectResponse.builder().build());

        assertThat(storageService.exists(KEY)).isTrue();
        verify(s3Client, times(2)).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void exists_unexpectedSdkException_wrappedAsExternalServiceException() {
        SdkException connectionReset = SdkException.builder().message("connection reset").build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(connectionReset);

        assertThatThrownBy(() -> storageService.exists(KEY))
                .isInstanceOf(ExternalServiceException.class);

        verify(s3Client, times(3)).headObject(any(HeadObjectRequest.class));
    }

    // --- delete — calls this class's own exists() first ---

    @Test
    void delete_keyDoesNotExist_returnsFalseWithoutCallingDeleteObject() {
        S3Exception notFound = (S3Exception) S3Exception.builder().statusCode(404).message("not found").build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(notFound);

        assertThat(storageService.delete(KEY)).isFalse();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_callsDeleteObjectWithCorrectKey() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        assertThat(storageService.delete(KEY)).isTrue();

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void delete_transientFailure_retriesThenSucceeds() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        S3Exception serverError = (S3Exception) S3Exception.builder().statusCode(500).message("internal error").build();
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(serverError)
                .thenReturn(DeleteObjectResponse.builder().build());

        assertThat(storageService.delete(KEY)).isTrue();
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }
}