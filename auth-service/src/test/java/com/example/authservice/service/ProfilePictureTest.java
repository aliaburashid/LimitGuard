package com.example.authservice.service;

import com.example.authservice.model.FinancialInstitution;
import com.example.authservice.model.User;
import com.example.authservice.repository.FinancialInstitutionRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.security.JWTUtils;
import com.example.authservice.security.MyUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_profile_picture;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "jwt-secret=auth-picture-tests-only-signing-key",
        "jwt-expiration-ms=60000"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ProfilePictureTest {

    private static final int FIVE_MB = 5 * 1024 * 1024;
    private static final String PICTURE_PATH = "/api/auth/users/profile-picture";

    @TempDir
    static Path checkout;

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTUtils jwt;

    @Autowired
    private UserRepository users;

    @Autowired
    private FinancialInstitutionRepository institutions;

    @MockitoBean
    private EmailService emailService;

    private final HttpClient http = HttpClient.newHttpClient();
    private User user;

    @DynamicPropertySource
    static void useTemporaryCheckout(DynamicPropertyRegistry properties) throws Exception {
        Files.createFile(checkout.resolve("pom.xml"));
        Files.createDirectory(checkout.resolve("frontend"));
        Path authDirectory = Files.createDirectory(checkout.resolve("auth-service"));
        Path pictureDirectory = UserService.resolveProfilePictureDirectory(authDirectory, "");
        properties.add("limitguard.profile-pictures.directory", pictureDirectory::toString);
    }

    @BeforeEach
    void createUser() {
        FinancialInstitution institution = new FinancialInstitution();
        institution.setName("Test institution " + UUID.randomUUID());

        User fixture = new User();
        fixture.setFirstName("Test");
        fixture.setLastName("User");
        fixture.setEmail(UUID.randomUUID() + "@example.test");
        fixture.setPassword("unused-test-value");
        fixture.setEmailVerified(true);
        fixture.setFinancialInstitution(institutions.save(institution));
        user = users.save(fixture);
    }

    @Test
    void uploadsAndRetrievesFourMbPngOverHttp() throws Exception {
        byte[] picture = Arrays.copyOf(png(), 4 * 1024 * 1024);
        assertThat(upload(picture, "image/png").statusCode()).isEqualTo(200);

        String storedPath = storedPath();
        assertThat(storedPath).matches("uploads/[0-9a-f-]{36}\\.png");
        assertThat(Files.readAllBytes(checkout.resolve(storedPath))).isEqualTo(picture);
        assertPicture(retrieve(), picture);
    }

    @Test
    void retrievesLegacyPictureFromCheckoutUploads() throws Exception {
        String filename = UUID.randomUUID() + ".png";
        byte[] picture = png();
        Path existingUploads = Files.createDirectories(checkout.resolve("uploads"));
        Files.write(existingUploads.resolve(filename), picture);
        user.setProfilePicturePath("uploads/" + filename);
        users.save(user);

        assertPicture(retrieve(), picture);
    }

    @Test
    void rejectsInvalidFilesWithoutReplacingThePicture() throws Exception {
        assertThat(upload(new byte[]{1, 2, 3}, "image/png").statusCode()).isEqualTo(400);
        assertThat(upload(png(), "image/jpeg").statusCode()).isEqualTo(400);
        assertThat(upload(png(), "text/plain").statusCode()).isEqualTo(400);
        assertThat(storedPath()).isNull();
        assertThat(retrieve().statusCode()).isEqualTo(404);
    }

    @Test
    void rejectsOversizedHttpFileAndMultipartRequest() throws Exception {
        assertThat(upload(new byte[FIVE_MB + 1], "image/png").statusCode()).isEqualTo(413);
        // a 5 MB part fits the file limit, but its multipart envelope exceeds the request limit
        assertThat(upload(new byte[FIVE_MB], "image/png").statusCode()).isEqualTo(413);
        assertThat(storedPath()).isNull();
    }

    @Test
    void existingFileValidationAcceptsFiveMbAndRejectsOneByteMore() throws Exception {
        // MockMvc supplies an already parsed part, allowing the service limit to be checked independently.
        byte[] picture = Arrays.copyOf(png(), FIVE_MB);
        mvc.perform(multipart(HttpMethod.PATCH, PICTURE_PATH)
                        .file(new MockMultipartFile("image", "test.png", "image/png", picture))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        String uploadedPath = storedPath();

        mvc.perform(multipart(HttpMethod.PATCH, PICTURE_PATH)
                        .file(new MockMultipartFile("image", "test.png", "image/png", new byte[FIVE_MB + 1]))
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Profile picture cannot exceed 5 MB"));
        assertThat(storedPath()).isEqualTo(uploadedPath);
    }

    private HttpResponse<byte[]> upload(byte[] image, String type) throws Exception {
        String boundary = "profile-picture-test";
        byte[] header = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"test.png\"\r\n"
                + "Content-Type: " + type + "\r\n\r\n").getBytes(UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(UTF_8);
        HttpRequest request = request()
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArrays(java.util.List.of(header, image, footer)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> retrieve() throws Exception {
        return http.send(request().GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest.Builder request() {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PICTURE_PATH))
                .header("Authorization", bearer());
    }

    private String bearer() {
        return "Bearer " + jwt.generateJwtToken(new MyUserDetails(user));
    }

    private String storedPath() {
        return users.findById(user.getId()).orElseThrow().getProfilePicturePath();
    }

    private void assertPicture(HttpResponse<byte[]> response, byte[] expected) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.body()).isEqualTo(expected);
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}
