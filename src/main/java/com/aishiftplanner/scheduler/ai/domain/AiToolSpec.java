package com.aishiftplanner.scheduler.ai.domain;

import java.util.List;

/**
 * The description of a backend tool that is offered to the model.
 *
 * <p>Note what this record does <em>not</em> contain: any way to execute anything. It is a
 * description handed to a model so it can say "I would like this one, with these arguments".
 * The mapping from that request to actual code — and the authorization check in between —
 * lives in the chat module, deliberately out of the model's reach.
 *
 * @param parameters the arguments the tool accepts, used to build the model-facing schema
 */
public record AiToolSpec(String name, String description, List<Parameter> parameters) {

    public record Parameter(String name, String type, String description, boolean required) {

        public static Parameter requiredString(String name, String description) {
            return new Parameter(name, "string", description, true);
        }

        public static Parameter optionalString(String name, String description) {
            return new Parameter(name, "string", description, false);
        }
    }
}
