/** 与服务端 RequirementText 相同：步骤合成一句，补充说明另起一行。 */
export function composeRequirement(steps: string[], supplement: string): string {
  const normalized = steps.map((step) => step.trim()).filter(Boolean);
  const sentence = sentenceOf(normalized);
  const extra = supplement.trim();
  if (!sentence) return extra;
  return extra ? `${sentence}\n${extra}` : sentence;
}

function sentenceOf(steps: string[]): string {
  if (steps.length <= 1) return steps[0] ?? '';
  if (steps.length === 2) return `先${steps[0]}，再${steps[1]}`;
  const middle = steps.slice(1, -1).map((step) => `，再${step}`).join('');
  return `先${steps[0]}${middle}，最后${steps[steps.length - 1]}`;
}
