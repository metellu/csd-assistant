package com.uwo.csd;

import com.amazon.ask.Skill;
import com.amazon.ask.Skills;
import com.amazon.ask.SkillStreamHandler;
import com.uwo.csd.handlers.*;

public class CsdAssistantStreamHandler extends SkillStreamHandler {

    private static Skill getSkill() {
        return Skills.standard()
                .addRequestHandlers(
                        new CancelandStopIntentHandler(),
                        new TimeTableIntentHandler(),
                        new CourseDescIntentHandler(),
                        new InstructorIntentHandler(),
                        new EnrollmentEligibilityIntentHandler(),
                        new CoursesWithinSpecifiedTermIntentHandler(),
                        new EventIntentHandler(),
                        new HelpIntentHandler(),
                        new LaunchRequestHandler(),
                        new SessionEndedRequestHandler(),
                        new FallbackIntentHandler())
                // Add your skill id below
                //.withSkillId("")
                .build();
    }

    public CsdAssistantStreamHandler() {
        super(getSkill());
    }

}

