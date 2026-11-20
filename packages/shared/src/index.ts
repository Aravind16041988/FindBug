import {z} from "zod";
export const StepSchema=z.discriminatedUnion("action",[
z.object({action:z.literal("goto"),url:z.string().url()}),
z.object({action:z.literal("click"),by:z.enum(["role","label","text"]),value:z.string(),role:z.string().optional()}),
z.object({action:z.literal("fill"),by:z.enum(["label","placeholder"]),value:z.string(),text:z.string()}),
z.object({action:z.literal("assertVisible"),by:z.enum(["role","label","text"]),value:z.string(),role:z.string().optional()}),
z.object({action:z.literal("assertText"),text:z.string()}),
z.object({action:z.literal("screenshot"),name:z.string().regex(/^[a-zA-Z0-9_-]+$/).default("checkpoint")})]);
export const PlanSchema=z.object({title:z.string().min(1).max(120),objective:z.string().min(1).max(1000),assumptions:z.array(z.string()).default([]),steps:z.array(StepSchema).min(1).max(30)});
export type TestPlan=z.infer<typeof PlanSchema>;export type TestStep=z.infer<typeof StepSchema>;