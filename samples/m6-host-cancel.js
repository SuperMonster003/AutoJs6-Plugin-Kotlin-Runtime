// Run this from AutoJs6 while m6-cancellation.kt is sleeping.
// The host cancels every active script through its public engines API.
console.log("M6_HOST_CANCEL_REQUESTED");
engines.stopAll();
