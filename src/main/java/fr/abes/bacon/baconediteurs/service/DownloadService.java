package fr.abes.bacon.baconediteurs.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class DownloadService {
    private final RestTemplate restTemplate;

    public DownloadService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean checkUrl(String url) {
        ResponseEntity<Void> responseEntity = restTemplate.exchange(url, HttpMethod.HEAD, null, void.class);
        return responseEntity.getStatusCode().is2xxSuccessful();
    }

    public ResponseEntity<byte[]> getRestCall(String url) throws RestClientException {
        return restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
    }
}
