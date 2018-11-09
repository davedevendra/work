package com.springboot.practice.controller.topic;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class TopicService {
    List<Topic> topics = Arrays.asList(
        new Topic("java", "Java Code", "Java Description"),
        new Topic("spring", "Spring Code", "Spring Description"),
        new Topic("scala", "Scala Code", "Scala Description") 
    );

    public List<Topic> getAllTopics() {
        return topics;
    }

    public Topic getTopic(String id) {
         return topics.stream().filter(t->t.getId().equals(id)).findFirst().get();
    }

    public void addTopic(Topic topic) {
        topics.add(topic);
    }

    public void updateTopic(Topic topic, String id) {
        for (int i = 0; i < topics.size(); i++) {
            Topic t = topics.get(i);
            if(t.getId().equals(id)){
                topics.set(i,topic);
            }
        }
    }

    public void removeTopic(String id) {
        topics.removeIf(t->t.getId().equals(id));
    }

 
}

